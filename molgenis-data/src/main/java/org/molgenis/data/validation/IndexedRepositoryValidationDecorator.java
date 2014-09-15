package org.molgenis.data.validation;

import org.molgenis.data.AggregateQuery;
import org.molgenis.data.AggregateResult;
import org.molgenis.data.IndexedCrudRepository;

public class IndexedRepositoryValidationDecorator extends RepositoryValidationDecorator implements
		IndexedCrudRepository
{
	private final IndexedCrudRepository decoratedRepository;

	public IndexedRepositoryValidationDecorator(IndexedCrudRepository decoratedRepository,
			EntityAttributesValidator entityAttributesValidator)
	{
		super(decoratedRepository, entityAttributesValidator);
		this.decoratedRepository = decoratedRepository;
	}

	@Override
	public AggregateResult aggregate(AggregateQuery aggregateQuery)
	{
		return decoratedRepository.aggregate(aggregateQuery);
	}

	@Override
	public void rebuildIndex()
	{
		decoratedRepository.rebuildIndex();
	}
}
