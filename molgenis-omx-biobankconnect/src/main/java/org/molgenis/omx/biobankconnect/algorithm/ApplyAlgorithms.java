package org.molgenis.omx.biobankconnect.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.molgenis.MolgenisFieldTypes;
import org.molgenis.data.DataService;
import org.molgenis.data.QueryRule;
import org.molgenis.data.QueryRule.Operator;
import org.molgenis.data.elasticsearch.SearchService;
import org.molgenis.data.elasticsearch.util.Hit;
import org.molgenis.data.elasticsearch.util.SearchRequest;
import org.molgenis.data.elasticsearch.util.SearchResult;
import org.molgenis.data.omx.OmxRepository;
import org.molgenis.data.support.MapEntity;
import org.molgenis.data.support.QueryImpl;
import org.molgenis.data.validation.DefaultEntityValidator;
import org.molgenis.data.validation.EntityAttributesValidator;
import org.molgenis.js.ScriptEvaluator;
import org.molgenis.omx.biobankconnect.wizard.CurrentUserStatus;
import org.molgenis.omx.biobankconnect.wizard.CurrentUserStatus.STAGE;
import org.molgenis.omx.dataset.DataSetMatrixRepository;
import org.molgenis.omx.datasetdeleter.DataSetDeleterService;
import org.molgenis.omx.observ.Category;
import org.molgenis.omx.observ.DataSet;
import org.molgenis.omx.observ.ObservableFeature;
import org.molgenis.omx.observ.ObservationSet;
import org.molgenis.omx.observ.ObservedValue;
import org.molgenis.omx.observ.value.CategoricalValue;
import org.molgenis.omx.observ.value.DecimalValue;
import org.molgenis.omx.observ.value.IntValue;
import org.molgenis.omx.observ.value.StringValue;
import org.molgenis.omx.observ.value.Value;
import org.molgenis.security.runas.RunAsSystem;
import org.molgenis.security.user.UserAccountService;
import org.mozilla.javascript.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Iterables;

public class ApplyAlgorithms
{
	@Autowired
	private DataService dataService;

	@Autowired
	private SearchService searchService;

	@Autowired
	private CurrentUserStatus currentUserStatus;

	@Autowired
	private UserAccountService userAccountService;
	@Autowired
	private DataSetDeleterService dataSetDeleterService;

	private static final String CATALOGUE_PREFIX = "protocolTree-";
	private static final String ENTITY_ID = "id";
	private static final String ENTITY_TYPE = "type";
	private static final String STORE_MAPPING_FEATURE = "store_mapping_feature";
	private static final String STORE_MAPPING_ALGORITHM_SCRIPT = "store_mapping_algorithm_script";

	private static final Map<String, Class<? extends Value>> entityMap = new HashMap<String, Class<? extends Value>>();
	static
	{
		entityMap.put(StringValue.ENTITY_NAME, StringValue.class);
		entityMap.put(IntValue.ENTITY_NAME, IntValue.class);
		entityMap.put(DecimalValue.ENTITY_NAME, DecimalValue.class);
		entityMap.put(CategoricalValue.ENTITY_NAME, CategoricalValue.class);
	}

	@Async
	@RunAsSystem
	@Transactional
	public void applyAlgorithm(String userName, Integer targetDataSetId, List<Integer> sourceDataSetIds)
	{
		currentUserStatus.setUserCurrentStage(userName, STAGE.DeleteMapping);
		removeExistingDerivedDataSets(userName, targetDataSetId, sourceDataSetIds);
		currentUserStatus.setUserCurrentStage(userName, STAGE.CreateMapping);
		createDerivedDataSets(userName, targetDataSetId, sourceDataSetIds);
		SearchResult allFeaturesResult = findAllFeatures(targetDataSetId);
		currentUserStatus.setUserTotalNumberOfQueries(userName,
				allFeaturesResult.getTotalHitCount() * sourceDataSetIds.size());

		QueryImpl query = new QueryImpl();
		query.pageSize(Integer.MAX_VALUE);
		for (Hit hit : allFeaturesResult.getSearchHits())
		{
			if (query.getRules().size() > 0) query.addRule(new QueryRule(Operator.OR));
			query.addRule(new QueryRule(STORE_MAPPING_FEATURE, Operator.EQUALS, hit.getColumnValueMap().get(ENTITY_ID)));
		}
		generateValues(userName, query, targetDataSetId, sourceDataSetIds);
		currentUserStatus.setUserCurrentStage(userName, STAGE.StoreMapping);
		searchService.indexRepository(new DataSetMatrixRepository(dataService, createDerivedDataSetIdentifier(userName,
				targetDataSetId.toString(), StringUtils.join(sourceDataSetIds, '-'))));
		currentUserStatus.setUserIsRunning(userName, false);
	}

	private void generateValues(String userName, QueryImpl query, Integer targetDataSetId,
			List<Integer> sourceDataSetIds)
	{
		String derivedDataSetIdentifier = createDerivedDataSetIdentifier(userName, targetDataSetId.toString(),
				StringUtils.join(sourceDataSetIds, '-'));
		DataSet derivedDataSet = dataService.findOne(DataSet.ENTITY_NAME,
				new QueryImpl().eq(DataSet.IDENTIFIER, derivedDataSetIdentifier), DataSet.class);
		Map<Integer, ObservationSet> observationSetMap = new HashMap<Integer, ObservationSet>();
		List<ObservedValue> listOfObservedValues = new ArrayList<ObservedValue>();
		for (Integer sourceDataSetId : sourceDataSetIds)
		{
			SearchResult searchResult = searchService.search(new SearchRequest(createMappingDataSetIdentifier(userName,
					targetDataSetId, sourceDataSetId), query, null));
			for (Hit hit : searchResult.getSearchHits())
			{
				Integer featureId = Integer.parseInt(hit.getColumnValueMap().get(STORE_MAPPING_FEATURE).toString());
				ObservableFeature feature = dataService.findOne(ObservableFeature.ENTITY_NAME, featureId,
						ObservableFeature.class);
				String algorithmScript = hit.getColumnValueMap().get(STORE_MAPPING_ALGORITHM_SCRIPT).toString();
				for (Entry<Integer, Object> entry : createValueFromAlgorithm(feature.getDataType(), sourceDataSetId,
						algorithmScript).entrySet())
				{
					Integer observationSetId = entry.getKey();
					if (!observationSetMap.containsKey(observationSetId))
					{
						ObservationSet observationSet = new ObservationSet();
						observationSet.setIdentifier(createObservationSetIdentifier(userName, targetDataSetId,
								sourceDataSetId, observationSetId));
						observationSet.setPartOfDataSet(derivedDataSet);
						observationSetMap.put(observationSetId, observationSet);
					}
					ObservedValue observedValue = new ObservedValue();
					observedValue.setFeature(feature);
					observedValue.setObservationSet(observationSetMap.get(observationSetId));
					addValueByType(feature, observedValue, entry.getValue());
					listOfObservedValues.add(observedValue);
				}
				currentUserStatus.incrementFinishedNumbersByOne(userName);
			}
		}
		dataService.add(ObservationSet.ENTITY_NAME, observationSetMap.values());
		dataService.add(ObservedValue.ENTITY_NAME, listOfObservedValues);
		dataService.getCrudRepository(ObservedValue.ENTITY_NAME).flush();
	}

	private void addValueByType(ObservableFeature feature, ObservedValue observedValue, Object value)
	{
		if (feature.getDataType().equalsIgnoreCase(MolgenisFieldTypes.FieldTypeEnum.INT.toString()))
		{
			IntValue intValue = new IntValue();
			intValue.setValue(Integer.parseInt(value.toString()));
			dataService.add(IntValue.ENTITY_NAME, intValue);
			observedValue.setValue(intValue);
		}
		else if (feature.getDataType().equalsIgnoreCase(MolgenisFieldTypes.FieldTypeEnum.DECIMAL.toString()))
		{
			DecimalValue decimalValue = new DecimalValue();
			decimalValue.setValue(Double.parseDouble(value.toString()));
			dataService.add(DecimalValue.ENTITY_NAME, decimalValue);
			observedValue.setValue(decimalValue);
		}
		else if (feature.getDataType().equalsIgnoreCase(MolgenisFieldTypes.FieldTypeEnum.STRING.toString()))
		{
			StringValue stringValue = new StringValue();
			stringValue.setValue(value.toString());
			dataService.add(StringValue.ENTITY_NAME, stringValue);
			observedValue.setValue(stringValue);
		}
		else if (feature.getDataType().equalsIgnoreCase(MolgenisFieldTypes.FieldTypeEnum.CATEGORICAL.toString()))
		{
			Category category = dataService.findOne(
					Category.ENTITY_NAME,
					new QueryImpl().eq(Category.OBSERVABLEFEATURE, feature).and()
							.eq(Category.VALUECODE, value.toString()), Category.class);
			CategoricalValue categoricalValue = new CategoricalValue();
			categoricalValue.setValue(category);
			dataService.add(CategoricalValue.ENTITY_NAME, categoricalValue);
			observedValue.setValue(categoricalValue);
		}
		else
		{
			// TODO : implement the rest of data types
		}
	}

	private void removeExistingDerivedDataSets(String userName, Integer targetDataSetId, List<Integer> sourceDataSetIds)
	{
		String dataSetIdentifier = createDerivedDataSetIdentifier(userName, targetDataSetId.toString(),
				StringUtils.join(sourceDataSetIds, '-'));
		DataSet derivedDataSet = dataService.findOne(DataSet.ENTITY_NAME,
				new QueryImpl().eq(DataSet.IDENTIFIER, dataSetIdentifier), DataSet.class);
		Map<String, List<Integer>> valuesByType = new HashMap<String, List<Integer>>();
		if (derivedDataSet != null)
		{
			Iterable<ObservationSet> observationSets = dataService.findAll(ObservationSet.ENTITY_NAME,
					new QueryImpl().eq(ObservationSet.PARTOFDATASET, derivedDataSet), ObservationSet.class);
			if (Iterables.size(observationSets) > 0)
			{
				Iterable<ObservedValue> observedValues = dataService.findAll(ObservedValue.ENTITY_NAME,
						new QueryImpl().in(ObservedValue.OBSERVATIONSET, observationSets), ObservedValue.class);
				currentUserStatus.setUserTotalNumberOfQueries(userName,
						(long) (Iterables.size(observedValues) * sourceDataSetIds.size()));
				for (ObservedValue value : observedValues)
				{
					String valueType = value.getValue().get__Type();
					Integer valueId = value.getId();
					if (!valuesByType.containsKey(valueType)) valuesByType.put(valueType, new ArrayList<Integer>());
					valuesByType.get(valueType).add(valueId);
				}
			}
			dataSetDeleterService.deleteData(dataSetIdentifier, false);
			dataService.delete(DataSet.ENTITY_NAME, derivedDataSet);
			for (Entry<String, List<Integer>> entryValuesByType : valuesByType.entrySet())
			{
				String valueType = entryValuesByType.getKey();
				List<Object> ids = new ArrayList<Object>(entryValuesByType.getValue());
				dataService.delete(valueType, dataService.findAll(valueType, ids));
				currentUserStatus.incrementFinishedNumbers(userName, ids.size());
			}
			dataService.getCrudRepository(ObservedValue.ENTITY_NAME).flush();
		}
	}

	private DataSet createDerivedDataSets(String userName, Integer targetDataSetId, List<Integer> sourceDataSetIds)
	{
		DataSet targetDataSet = dataService.findOne(DataSet.ENTITY_NAME, targetDataSetId, DataSet.class);
		DataSet derivedDataSet = new DataSet();
		derivedDataSet.setIdentifier(createDerivedDataSetIdentifier(userName, targetDataSet.getId().toString(),
				sourceDataSetIds));
		derivedDataSet.setName(createDerivedDataSetName(userName, targetDataSet.getName()));
		derivedDataSet.setProtocolUsed(targetDataSet.getProtocolUsed());
		dataService.add(DataSet.ENTITY_NAME, derivedDataSet);
		dataService.getCrudRepository(DataSet.ENTITY_NAME).flush();

		if (!dataService.hasRepository(derivedDataSet.getIdentifier()))
		{
			dataService.addRepository(new OmxRepository(dataService, searchService, derivedDataSet.getIdentifier(),
					new DefaultEntityValidator(dataService, new EntityAttributesValidator())));
		}
		return derivedDataSet;
	}

	private SearchResult findAllFeatures(Integer targetDataSetId)
	{
		QueryImpl query = new QueryImpl();
		query.addRule(new QueryRule(ENTITY_TYPE, Operator.EQUALS, ObservableFeature.class.getSimpleName().toLowerCase()));
		query.pageSize(1000000);
		DataSet dataSet = dataService.findOne(DataSet.ENTITY_NAME, targetDataSetId, DataSet.class);
		return searchService
				.search(new SearchRequest(CATALOGUE_PREFIX + dataSet.getProtocolUsed().getId(), query, null));
	}

	public String validateAlgorithmInputs(Integer dataSetId, String algorithm)
	{
		if (StringUtils.isEmpty(algorithm))
		{
			return ("Algorithm is not defined!");
		}

		Iterable<ObservableFeature> featureIterators = dataService.findAll(ObservableFeature.ENTITY_NAME,
				new QueryImpl().in(ObservableFeature.NAME, extractFeatureName(algorithm)), ObservableFeature.class);
		if (Iterables.size(featureIterators) == 0)
		{
			return ("Variables are not found! Please check the name of variables!");
		}

		DataSet sourceDataSet = dataService.findOne(DataSet.ENTITY_NAME, dataSetId, DataSet.class);
		Iterable<ObservationSet> observationSets = dataService.findAll(ObservationSet.ENTITY_NAME,
				new QueryImpl().eq(ObservationSet.PARTOFDATASET, sourceDataSet), ObservationSet.class);
		if (Iterables.size(observationSets) == 0)
		{
			return ("There are not rows in this dataset!");
		}

		return StringUtils.EMPTY;
	}

	public Map<Integer, Object> createValueFromAlgorithm(String dataType, Integer sourceDataSetId,
			String algorithmScript)
	{
		if (StringUtils.isEmpty(algorithmScript)) return Collections.emptyMap();
		List<String> featureNames = extractFeatureName(algorithmScript);
		Iterable<ObservableFeature> featureIterators = dataService.findAll(ObservableFeature.ENTITY_NAME,
				new QueryImpl().in(ObservableFeature.NAME, featureNames), ObservableFeature.class);

		DataSet sourceDataSet = dataService.findOne(DataSet.ENTITY_NAME, sourceDataSetId, DataSet.class);
		Iterable<ObservationSet> observationSets = dataService.findAll(ObservationSet.ENTITY_NAME,
				new QueryImpl().eq(ObservationSet.PARTOFDATASET, sourceDataSet), ObservationSet.class);
		Iterable<ObservedValue> observedValueIterators = dataService.findAll(
				ObservedValue.ENTITY_NAME,
				new QueryImpl().and().in(ObservedValue.FEATURE, featureIterators).and()
						.in(ObservedValue.OBSERVATIONSET, observationSets), ObservedValue.class);

		Map<Integer, MapEntity> eachIndividualValues = new HashMap<Integer, MapEntity>();
		for (ObservedValue value : observedValueIterators)
		{
			ObservationSet observationSet = value.getObservationSet();
			Integer observationSetId = observationSet.getId();
			if (!eachIndividualValues.containsKey(observationSetId)) eachIndividualValues.put(observationSetId,
					new MapEntity());
			Object valueObject = value.getValue().get("value");

			if (valueObject instanceof String)
			{
				eachIndividualValues.get(observationSetId).set(value.getFeature().getName(),
						value.getValue().get("value").toString());
			}
			else if (valueObject instanceof Integer)
			{
				eachIndividualValues.get(observationSetId).set(value.getFeature().getName(),
						Integer.parseInt(value.getValue().get("value").toString()));
			}
			else if (valueObject instanceof Double)
			{
				eachIndividualValues.get(observationSetId).set(value.getFeature().getName(),
						Double.parseDouble(value.getValue().get("value").toString()));
			}
			else if (valueObject instanceof Category)
			{
				eachIndividualValues.get(observationSetId).set(value.getFeature().getName(),
						Integer.parseInt(((Category) value.getValue().get("value")).getValueCode()));
			}
		}

		Map<Integer, Object> calculatedResults = new HashMap<Integer, Object>();
		for (Entry<Integer, MapEntity> entry : eachIndividualValues.entrySet())
		{
			if (Iterables.size(entry.getValue().getAttributeNames()) != featureNames.size()) continue;
			Object result = ScriptEvaluator.eval(algorithmScript, entry.getValue());
			if (dataType.equalsIgnoreCase(MolgenisFieldTypes.FieldTypeEnum.INT.toString())) calculatedResults.put(
					entry.getKey(), Integer.parseInt(Context.toString(result)));
			else if (dataType.equalsIgnoreCase(MolgenisFieldTypes.FieldTypeEnum.CATEGORICAL.toString())) calculatedResults
					.put(entry.getKey(), Integer.parseInt(Context.toString(result)));
			else if (dataType.equalsIgnoreCase(MolgenisFieldTypes.FieldTypeEnum.DECIMAL.toString())) calculatedResults
					.put(entry.getKey(), Context.toNumber(result));
			else calculatedResults.put(entry.getKey(), Context.toString(result));
		}
		return calculatedResults;
	}

	public static List<String> extractFeatureName(String algorithmScript)
	{
		List<String> featureNames = new ArrayList<String>();
		Pattern pattern = Pattern.compile("\\$\\('([^\\$\\(\\)]*)'\\)");
		Matcher matcher = pattern.matcher(algorithmScript);
		while (matcher.find())
		{
			if (!featureNames.contains(matcher.group(1))) featureNames.add(matcher.group(1));
		}
		if (featureNames.size() > 0) return featureNames;

		pattern = Pattern.compile("\\$\\(([^\\$\\(\\)]*)\\)");
		matcher = pattern.matcher(algorithmScript);
		while (matcher.find())
		{
			if (!featureNames.contains(matcher.group(1))) featureNames.add(matcher.group(1));
		}
		return featureNames;
	}

	private String createObservationSetIdentifier(String userName, Integer targetDataSetId, Integer sourceDataSetId,
			Integer observationSetId)
	{
		StringBuilder observationSetIdentifier = new StringBuilder();
		observationSetIdentifier.append(userName).append('-').append(targetDataSetId).append('-')
				.append(sourceDataSetId).append('-').append(observationSetId);
		return observationSetIdentifier.toString();
	}

	public static String createDerivedDataSetIdentifier(String userName, String targetDataSetId,
			List<Integer> sourceDataSetIds)
	{
		Collections.sort(sourceDataSetIds);
		return createDerivedDataSetIdentifier(userName, targetDataSetId, StringUtils.join(sourceDataSetIds, '-'));
	}

	public static String createDerivedDataSetIdentifier(String userName, String targetDataSetId, String sourceDataSetId)
	{
		StringBuilder dataSetIdentifier = new StringBuilder();
		dataSetIdentifier.append(userName).append('-').append(targetDataSetId).append('-').append(sourceDataSetId)
				.append("-derived");
		return dataSetIdentifier.toString();
	}

	private String createDerivedDataSetName(String userName, String targetDataSetName)
	{
		StringBuilder dataSetIdentifier = new StringBuilder();
		dataSetIdentifier.append(userName).append(": derived dataset for ").append(targetDataSetName);
		return dataSetIdentifier.toString();
	}

	private String createMappingDataSetIdentifier(String userName, Integer targetDataSetId, Integer sourceDataSetId)
	{
		StringBuilder dataSetIdentifier = new StringBuilder();
		dataSetIdentifier.append(userName).append('-').append(targetDataSetId).append('-').append(sourceDataSetId);
		return dataSetIdentifier.toString();
	}
}