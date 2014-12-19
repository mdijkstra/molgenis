package org.molgenis.ontology.repository;

import java.util.Iterator;

import org.molgenis.data.Entity;
import org.molgenis.data.Query;
import org.molgenis.data.Queryable;
import org.molgenis.data.elasticsearch.SearchService;
import org.molgenis.data.support.QueryImpl;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractOntologyQueryRepository extends AbstractOntologyRepository implements Queryable
{
	@Autowired
	public AbstractOntologyQueryRepository(String entityName, SearchService searchService)
	{
		super(entityName, searchService);
	}

	public Entity findOneInternal(Query query)
	{
		for (Entity entity : searchService.search(query, getEntityMetaData()))
		{
			return entity;
		}

		return null;
	}

	@Override
	public Iterator<Entity> iterator()
	{
		return findAll(new QueryImpl()).iterator();
	}

	@Override
	public Iterable<Entity> findAll(Iterable<Object> ids)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public long count()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public <E extends Entity> Iterable<E> findAll(Query q, Class<E> clazz)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public <E extends Entity> Iterable<E> findAll(Iterable<Object> ids, Class<E> clazz)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public <E extends Entity> E findOne(Object id, Class<E> clazz)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public <E extends Entity> E findOne(Query q, Class<E> clazz)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public <E extends Entity> Iterable<E> iterator(Class<E> clazz)
	{
		throw new UnsupportedOperationException();
	}
}
