package org.molgenis.charts.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.molgenis.MolgenisFieldTypes.FieldTypeEnum;
import org.molgenis.charts.BoxPlotChart;
import org.molgenis.charts.ChartDataService;
import org.molgenis.charts.MolgenisAxisType;
import org.molgenis.charts.MolgenisChartException;
import org.molgenis.charts.MolgenisSerieType;
import org.molgenis.charts.XYDataChart;
import org.molgenis.charts.calculations.BoxPlotCalcUtil;
import org.molgenis.data.DataService;
import org.molgenis.data.Entity;
import org.molgenis.data.EntityMetaData;
import org.molgenis.data.Query;
import org.molgenis.data.QueryRule;
import org.molgenis.data.Repository;
import org.molgenis.data.support.QueryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class ChartDataServiceImpl implements ChartDataService
{
	private final DataService dataService;

	@Autowired
	public ChartDataServiceImpl(DataService dataService)
	{
		if (dataService == null) throw new IllegalArgumentException("dataService is null");
		this.dataService = dataService;
	}

	@Override
	public XYDataChart getXYDataChart(String entityName, String attributeNameXaxis, String attributeNameYaxis,
			String split, List<QueryRule> queryRules)
	{
		Repository repo = dataService.getRepository(entityName);
		EntityMetaData entityMetaData = repo.getEntityMetaData();

		final FieldTypeEnum attributeXFieldTypeEnum = entityMetaData.getAttribute(attributeNameXaxis).getDataType()
				.getEnumType();
		final FieldTypeEnum attributeYFieldTypeEnum = entityMetaData.getAttribute(attributeNameYaxis).getDataType()
				.getEnumType();
		final List<XYDataSerie> xYDataSeries;

		// Sanity check
		if (FieldTypeEnum.DECIMAL.equals(attributeXFieldTypeEnum) || FieldTypeEnum.INT.equals(attributeXFieldTypeEnum)
				|| FieldTypeEnum.LONG.equals(attributeXFieldTypeEnum)
				|| FieldTypeEnum.DATE.equals(attributeXFieldTypeEnum)
				|| FieldTypeEnum.DATE_TIME.equals(attributeXFieldTypeEnum)
				|| FieldTypeEnum.DECIMAL.equals(attributeYFieldTypeEnum)
				|| FieldTypeEnum.INT.equals(attributeYFieldTypeEnum)
				|| FieldTypeEnum.LONG.equals(attributeYFieldTypeEnum)
				|| FieldTypeEnum.DATE.equals(attributeYFieldTypeEnum)
				|| FieldTypeEnum.DATE_TIME.equals(attributeYFieldTypeEnum))
		{
			if (!StringUtils.isNotBlank(split))
			{
				xYDataSeries = Arrays.asList(this.getXYDataSerie(repo, entityName, attributeNameXaxis,
						attributeNameYaxis, attributeXFieldTypeEnum, attributeYFieldTypeEnum, queryRules));
			}
			else
			{
				xYDataSeries = this.getXYDataSeries(repo, entityName, attributeNameXaxis, attributeNameYaxis,
						attributeXFieldTypeEnum, attributeYFieldTypeEnum, split, queryRules);
			}

			return new XYDataChart(xYDataSeries, MolgenisAxisType.getType(attributeXFieldTypeEnum),
					MolgenisAxisType.getType(attributeYFieldTypeEnum));
		}
		else
		{
			throw new MolgenisChartException(
					"For the x and the y axis selected datatype are wrong. Scatterplots can only handle Continuous data");
		}
	}

	@Override
	public XYDataSerie getXYDataSerie(Repository repo, String entityName, String attributeNameXaxis,
			String attributeNameYaxis, FieldTypeEnum attributeXFieldTypeEnum, FieldTypeEnum attributeYFieldTypeEnum,
			List<QueryRule> queryRules)
	{
		EntityMetaData entityMetaData = repo.getEntityMetaData();

		XYDataSerie serie = new XYDataSerie();
		serie.setName(entityMetaData.getAttribute(attributeNameXaxis).getLabel() + " vs "
				+ entityMetaData.getAttribute(attributeNameYaxis).getLabel());
		serie.setAttributeXFieldTypeEnum(attributeXFieldTypeEnum);
		serie.setAttributeYFieldTypeEnum(attributeYFieldTypeEnum);

		Sort sort = new Sort(Sort.DEFAULT_DIRECTION, attributeNameXaxis, attributeNameYaxis);
		Iterable<? extends Entity> iterable = getIterable(entityName, repo, queryRules, sort);
		for (Entity entity : iterable)
		{
			Object x = getJavaValue(entity, attributeNameXaxis, attributeXFieldTypeEnum);
			Object y = getJavaValue(entity, attributeNameYaxis, attributeYFieldTypeEnum);
			serie.addData(new XYData(x, y));
		}

		return serie;
	}

	@Override
	public List<XYDataSerie> getXYDataSeries(Repository repo, String entityName, String attributeNameXaxis,
			String attributeNameYaxis, FieldTypeEnum attributeXFieldTypeEnum, FieldTypeEnum attributeYFieldTypeEnum,
			String split, List<QueryRule> queryRules)

	{
		Sort sort = new Sort(Sort.DEFAULT_DIRECTION, attributeNameXaxis, attributeNameYaxis);
		Iterable<? extends Entity> iterable = getIterable(entityName, repo, queryRules, sort);

		Map<String, XYDataSerie> xYDataSeriesMap = new HashMap<String, XYDataSerie>();
		for (Entity entity : iterable)
		{
			String splitValue = createSplitKey(entity, split);
			if (!xYDataSeriesMap.containsKey(splitValue))
			{
				XYDataSerie serie = new XYDataSerie();
				serie.setName(splitValue);
				serie.setAttributeXFieldTypeEnum(attributeXFieldTypeEnum);
				serie.setAttributeYFieldTypeEnum(attributeYFieldTypeEnum);
				xYDataSeriesMap.put(splitValue, serie);
			}

			Object x = getJavaValue(entity, attributeNameXaxis, attributeXFieldTypeEnum);
			Object y = getJavaValue(entity, attributeNameYaxis, attributeYFieldTypeEnum);
			xYDataSeriesMap.get(splitValue).addData(new XYData(x, y));
		}

		List<XYDataSerie> series = new ArrayList<XYDataSerie>();
		for (Entry<String, XYDataSerie> serie : xYDataSeriesMap.entrySet())
		{
			series.add(serie.getValue());
		}

		return series;
	}

	private String getValueAsString(Entity entity, String split)
	{
		Object o = entity.get(split);
		if (o instanceof Entity)
		{
			return ((Entity) o).getLabelValue();
		}
		else if (o instanceof List)
		{
			@SuppressWarnings("unchecked")
			Iterable<Object> refObjects = (Iterable<Object>) o;
			StringBuilder strBuilder = new StringBuilder();
			for (Object ob : refObjects)
			{
				if (strBuilder.length() > 0)
				{
					strBuilder.append(',');
				}

				if (ob instanceof Entity)
				{
					strBuilder.append(((Entity) ob).getLabelValue());
				}
				else
				{
					strBuilder.append(ob.toString());
				}
			}
			return strBuilder.toString();
		}
		else
		{
			return "" + o;
		}
	}

	public String createSplitKey(Entity entity, String split)
	{
		return split + '(' + getValueAsString(entity, split) + ')';
	}

	@Override
	/**
	 * init a box plot chart
	 * 
	 * with:
	 *  1. box plot series as the boxes
	 *  2. xy data series as the outliers
	 *  3. categories names of the different box plots
	 *  
	 *  @param boxPlotChart (BoxPlotChart) the chart to be initialized 
	 *  @param repo (Repository<? extends Entity>) the repository where the data exists 
	 *  @param entityName (String) the name of the entity to be used
	 *  @param attributeName (String) the name of the observable value in the entity
	 *  @param queryRules (List<QueryRule>) the query rules to be used getting the data from the repo
	 *  @param split (String) the name of observable value where the data needs to split on 
	 *  @param scaleToCalcOutliers (double) the scale that need to be used calculating the outliers. 
	 *  	value 1 means that there wil not be any outliers.
	 */
	public BoxPlotChart getBoxPlotChart(String entityName, String attributeName, List<QueryRule> queryRules,
			String split, double scaleToCalcOutliers)
	{
		Repository repo = dataService.getRepository(entityName);
		EntityMetaData entityMetaData = repo.getEntityMetaData();

		BoxPlotChart boxPlotChart = new BoxPlotChart();
		boxPlotChart.setyLabel(entityMetaData.getAttribute(attributeName).getLabel());

		Sort sort = new Sort(Sort.DEFAULT_DIRECTION, attributeName);
		Iterable<Entity> iterable = getIterable(entityName, repo, queryRules, sort);
		Map<String, List<Double>> boxPlotDataListMap = getBoxPlotDataListMap(entityMetaData, iterable, attributeName,
				split);

		BoxPlotSerie boxPlotSerie = new BoxPlotSerie();
		boxPlotSerie.setType(MolgenisSerieType.BOXPLOT);
		boxPlotSerie.setName("Boxplot");

		XYDataSerie xYDataSerie = new XYDataSerie();
		xYDataSerie.setType(MolgenisSerieType.SCATTER);
		xYDataSerie.setName("Outliers");

		List<String> categories = new ArrayList<String>();

		int count = 0;
		for (Entry<String, List<Double>> entry : boxPlotDataListMap.entrySet())
		{

			List<Double> list = entry.getValue();
			categories.add(entry.getKey());
			Double[] data = BoxPlotCalcUtil.calcBoxPlotValues(entry.getValue());
			double iqr = BoxPlotCalcUtil.iqr(data[3], data[1]);
			double step = iqr * scaleToCalcOutliers;
			double highBorder = step + data[3];
			double lowBorder = data[1] - step;

			List<XYData> outlierList = new ArrayList<XYData>();
			List<Double> normalList = new ArrayList<Double>();
			for (Double o : list)
			{
				if (null != o)
				{
					if (o < lowBorder || o > highBorder)
					{
						outlierList.add(new XYData(count, o));
					}
					else
					{
						normalList.add(o);
					}
				}
			}

			xYDataSerie.addData(outlierList);
			data = BoxPlotCalcUtil.calcBoxPlotValues(normalList);
			boxPlotSerie.addData(data);
			count++;
		}

		boxPlotChart.addBoxPlotSerie(boxPlotSerie);
		boxPlotChart.addXYDataSerie(xYDataSerie);
		boxPlotChart.setCategories(categories);
		return boxPlotChart;
	}

	/**
	 * Get a map containing keys to different data lists
	 * 
	 * @param entityMeta
	 * @param iterable
	 * @param attributeName
	 * @param split
	 *            (String) if null or empty String will not split
	 * @return map (Map<String, List<Double>>)
	 */
	private Map<String, List<Double>> getBoxPlotDataListMap(EntityMetaData entityMeta, Iterable<Entity> iterable,
			String attributeName, String split)
	{
		Map<String, List<Double>> boxPlotDataListMap = new HashMap<String, List<Double>>();
		final boolean splitList = StringUtils.isNotBlank(split);

		if (splitList)
		{
			for (Entity entity : iterable)
			{
				String key = createSplitKey(entity, split);
				if (!boxPlotDataListMap.containsKey(key))
				{
					boxPlotDataListMap.put(key, new ArrayList<Double>());
				}
				boxPlotDataListMap.get(key).add(entity.getDouble(attributeName));
			}
		}
		else
		{
			String key = entityMeta.getAttribute(attributeName).getLabel();
			List<Double> list = new ArrayList<Double>();
			for (Entity entity : iterable)
			{
				list.add(entity.getDouble(attributeName));
			}
			boxPlotDataListMap.put(key, list);
		}
		return boxPlotDataListMap;
	}

	/**
	 * get a Iterable holding entitys
	 * 
	 * @param entityName
	 *            (String) the name of the entity to be used
	 * @param repo
	 *            (Repository<? extends Entity>) the repository where the data exists
	 * @param queryRules
	 *            (List<QueryRule>) the query rules to be used getting the data from the repo
	 * @param sort
	 *            (Sort)
	 * @return
	 */

	private Iterable<Entity> getIterable(String entityName, Repository repo, List<QueryRule> queryRules, Sort sort)
	{

		final Query q;
		if (queryRules == null)
		{
			q = new QueryImpl();
		}
		else
		{
			q = new QueryImpl(queryRules);
		}

		if (null != sort)
		{
			q.sort(sort);
		}

		return repo.findAll(q);
	}

	/**
	 * retrieves the value of the designated column as attributeJavaType
	 * 
	 * @param entity
	 * @param attributeName
	 * @param attributeJavaType
	 * @return value (Object)
	 */
	private Object getJavaValue(Entity entity, String attributeName, FieldTypeEnum attributeFieldTypeEnum)
	{
		if (FieldTypeEnum.DECIMAL.equals(attributeFieldTypeEnum))
		{
			return entity.getDouble(attributeName);
		}
		else if (FieldTypeEnum.INT.equals(attributeFieldTypeEnum))
		{
			return entity.getInt(attributeName);
		}
		else if (FieldTypeEnum.LONG.equals(attributeFieldTypeEnum))
		{
			return entity.getLong(attributeName);
		}
		else if (FieldTypeEnum.DATE_TIME.equals(attributeFieldTypeEnum))
		{
			return entity.getUtilDate(attributeName);
		}
		else if (FieldTypeEnum.DATE.equals(attributeFieldTypeEnum))
		{
			return entity.getDate(attributeName);
		}
		else
		{
			return entity.getString(attributeName);
		}
	}

	@Override
	public DataMatrix getDataMatrix(String entityName, List<String> attributeNamesXaxis, String attributeNameYaxis,
			List<QueryRule> queryRules)
	{
		Iterable<Entity> iterable = dataService.getRepository(entityName);

		if (queryRules != null && !queryRules.isEmpty())
		{
			QueryImpl q = new QueryImpl();
			for (QueryRule queryRule : queryRules)
			{
				q.addRule(queryRule);
			}

			iterable = ((Repository) iterable).findAll(q);
		}

		List<Target> rowTargets = new ArrayList<Target>();
		List<Target> columnTargets = new ArrayList<Target>();
		List<List<Number>> values = new ArrayList<List<Number>>();

		for (String columnTargetName : attributeNamesXaxis)
		{
			columnTargets.add(new Target(columnTargetName));
		}

		for (Entity entity : iterable)
		{
			String rowTargetName = entity.getString(attributeNameYaxis) != null ? entity.getString(attributeNameYaxis) : "";
			rowTargets.add(new Target(rowTargetName));

			List<Number> rowValues = new ArrayList<Number>();
			for (String attr : attributeNamesXaxis)
			{
				rowValues.add(entity.getDouble(attr));
			}
			values.add(rowValues);
		}

		return new DataMatrix(columnTargets, rowTargets, values);
	}
}
