package org.molgenis.data.csv;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.molgenis.MolgenisFieldTypes;
import org.molgenis.data.AttributeMetaData;
import org.molgenis.data.Entity;
import org.molgenis.data.EntityMetaData;
import org.molgenis.data.RepositoryCapability;
import org.molgenis.data.processor.CellProcessor;
import org.molgenis.data.support.AbstractRepository;
import org.molgenis.data.support.DefaultAttributeMetaData;
import org.molgenis.data.support.DefaultEntityMetaData;
import org.molgenis.data.support.MapEntity;
import org.springframework.util.StringUtils;

import com.google.common.collect.Iterables;

/**
 * Repository implementation for csv files.
 * 
 * The filename without the extension is considered to be the entityname
 */
public class CsvRepository extends AbstractRepository
{
	private final String sheetName;
	private final File file;
	private List<CellProcessor> cellProcessors;
	private DefaultEntityMetaData entityMetaData;
	private Character separator = null;

	public CsvRepository(String file)
	{
		this(new File(file), null);
	}

	public CsvRepository(File file, @Nullable List<CellProcessor> cellProcessors, Character separator)
	{
		this(file, StringUtils.stripFilenameExtension(file.getName()), null);
		this.separator = separator;
	}

	public CsvRepository(File file, @Nullable List<CellProcessor> cellProcessors)
	{
		this(file, StringUtils.stripFilenameExtension(file.getName()), null);
	}

	public CsvRepository(File file, String sheetName, @Nullable List<CellProcessor> cellProcessors)
	{
		this.file = file;
		this.sheetName = sheetName;
		this.cellProcessors = cellProcessors;
	}

	@Override
	public Iterator<Entity> iterator()
	{
		return new CsvIterator(file, sheetName, cellProcessors, separator);
	}

	@Override
	public EntityMetaData getEntityMetaData()
	{
		if (entityMetaData == null)
		{
			entityMetaData = new DefaultEntityMetaData(sheetName, MapEntity.class);

			for (String attrName : new CsvIterator(file, sheetName, null, separator).getColNamesMap().keySet())
			{
				AttributeMetaData attr = new DefaultAttributeMetaData(attrName, MolgenisFieldTypes.FieldTypeEnum.STRING);
				entityMetaData.addAttributeMetaData(attr);
			}
		}

		return entityMetaData;
	}

	public void addCellProcessor(CellProcessor cellProcessor)
	{
		if (cellProcessors == null) cellProcessors = new ArrayList<CellProcessor>();
		cellProcessors.add(cellProcessor);
	}

	@Override
	public Set<RepositoryCapability> getCapabilities()
	{
		return Collections.emptySet();
	}

	@Override
	public long count()
	{
		return Iterables.size(this);
	}

}
