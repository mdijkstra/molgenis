package org.molgenis.data.elasticsearch.util;

import org.molgenis.data.Entity;
import org.molgenis.data.EntityMetaData;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

public class ElasticsearchEntityUtils
{
	private ElasticsearchEntityUtils()
	{
	}

	public static String toElasticsearchId(Object entityId)
	{
		return entityId.toString();
	}

	public static Iterable<String> toElasticsearchIds(Iterable<Object> entityIds)
	{
		return Iterables.transform(entityIds, new Function<Object, String>()
		{
			@Override
			public String apply(Object entityId)
			{
				return toElasticsearchId(entityId);
			}
		});
	}

	public static Object toEntityId(String elasticsearchId)
	{
		// TODO we do not know which type to return (e.g. String, Integer)
		return elasticsearchId;
	}

	public static Iterable<Object> toEntityIds(Iterable<String> elasticsearchIds)
	{
		return Iterables.transform(elasticsearchIds, new Function<String, Object>()
		{
			@Override
			public Object apply(String elasticsearchId)
			{
				return toEntityId(elasticsearchId);
			}
		});
	}

	public static String toElasticsearchId(Entity entity, EntityMetaData entityMetaData)
	{
		String idAttributeName = entityMetaData.getIdAttribute().getName();
		Object entityId = entity.get(idAttributeName);
		return toElasticsearchId(entityId);
	}
}
