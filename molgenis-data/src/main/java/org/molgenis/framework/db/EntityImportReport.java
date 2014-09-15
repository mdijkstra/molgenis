package org.molgenis.framework.db;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

public class EntityImportReport implements Serializable
{
	private static final long serialVersionUID = 1L;

	private final Map<String, Integer> nrImportedEntitiesMap;
	private final List<String> newEntities = Lists.newArrayList();

	public EntityImportReport()
	{
		nrImportedEntitiesMap = new HashMap<String, Integer>();
	}

	public void addEntityCount(String entityName, int count)
	{
		Integer entityCount = nrImportedEntitiesMap.get(entityName);
		if (entityCount == null)
		{
			entityCount = 0;
			nrImportedEntitiesMap.put(entityName, entityCount);
		}
		nrImportedEntitiesMap.put(entityName, entityCount + count);
	}

	public Map<String, Integer> getNrImportedEntitiesMap()
	{
		return nrImportedEntitiesMap;
	}

	public void addNewEntity(String entityName)
	{
		newEntities.add(entityName);
	}

	public List<String> getNewEntities()
	{
		return newEntities;
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();

		for (String entity : nrImportedEntitiesMap.keySet())
		{
			sb.append("Imported ").append(nrImportedEntitiesMap.get(entity)).append(" ").append(entity)
					.append(" entities.<br />");
		}

		return sb.toString();
	}

}
