package org.molgenis.omx.biobankconnect.ontologytree;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.molgenis.data.Entity;
import org.molgenis.data.elasticsearch.SearchService;
import org.molgenis.data.elasticsearch.util.Hit;
import org.molgenis.data.elasticsearch.util.SearchRequest;
import org.molgenis.data.elasticsearch.util.SearchResult;
import org.molgenis.data.support.QueryImpl;
import org.molgenis.omx.biobankconnect.ontology.repository.OntologyTermIndexRepository;
import org.molgenis.omx.biobankconnect.ontology.repository.OntologyTermQueryRepository;
import org.molgenis.omx.biobankconnect.ontologyindexer.AsyncOntologyIndexer;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class OntologyTermIndexRepositoryTest
{
	OntologyTermQueryRepository ontologyTermIndexRepository;
	String ontologyIRI = "http://www.ontology.test";

	@BeforeClass
	public void setUp() throws OWLOntologyCreationException
	{

		Map<String, Object> columnValueMap1 = new HashMap<String, Object>();
		columnValueMap1.put(OntologyTermIndexRepository.ENTITY_TYPE, OntologyTermIndexRepository.TYPE_ONTOLOGYTERM);
		columnValueMap1.put(OntologyTermIndexRepository.ONTOLOGY_IRI, ontologyIRI);
		columnValueMap1.put(OntologyTermIndexRepository.ONTOLOGY_NAME, "test ontology");
		columnValueMap1.put(OntologyTermIndexRepository.LAST, false);
		columnValueMap1.put(OntologyTermIndexRepository.ROOT, true);
		columnValueMap1.put(OntologyTermIndexRepository.NODE_PATH, "1.2");
		columnValueMap1.put(OntologyTermIndexRepository.ONTOLOGY_TERM_IRI, ontologyIRI + "#term1");
		columnValueMap1.put(OntologyTermIndexRepository.ONTOLOGY_TERM, "ontology term 1");
		columnValueMap1.put(OntologyTermIndexRepository.SYNONYMS, "OT-1");
		Hit hit1 = mock(Hit.class);
		when(hit1.getId()).thenReturn("ontology-1");
		when(hit1.getColumnValueMap()).thenReturn(columnValueMap1);

		Map<String, Object> columnValueMap2 = new HashMap<String, Object>();
		columnValueMap2.put(OntologyTermIndexRepository.ENTITY_TYPE, OntologyTermIndexRepository.TYPE_ONTOLOGYTERM);
		columnValueMap2.put(OntologyTermIndexRepository.ONTOLOGY_IRI, ontologyIRI);
		columnValueMap2.put(OntologyTermIndexRepository.ONTOLOGY_NAME, "test ontology");
		columnValueMap2.put(OntologyTermIndexRepository.LAST, false);
		columnValueMap2.put(OntologyTermIndexRepository.ROOT, false);
		columnValueMap2.put(OntologyTermIndexRepository.NODE_PATH, "1.2.3");
		columnValueMap2.put(OntologyTermIndexRepository.PARENT_NODE_PATH, "1.2");
		columnValueMap2.put(OntologyTermIndexRepository.PARENT_ONTOLOGY_TERM_IRI, ontologyIRI + "#term1");
		columnValueMap2.put(OntologyTermIndexRepository.ONTOLOGY_TERM_IRI, ontologyIRI + "#term2");
		columnValueMap2.put(OntologyTermIndexRepository.ONTOLOGY_TERM, "ontology term 2");
		columnValueMap2.put(OntologyTermIndexRepository.SYNONYMS, "OT-2");
		Hit hit2 = mock(Hit.class);
		when(hit2.getId()).thenReturn("ontology-2");
		when(hit2.getColumnValueMap()).thenReturn(columnValueMap2);

		Map<String, Object> columnValueMap3 = new HashMap<String, Object>();
		columnValueMap3.put(OntologyTermIndexRepository.ENTITY_TYPE, OntologyTermIndexRepository.TYPE_ONTOLOGYTERM);
		columnValueMap3.put(OntologyTermIndexRepository.ONTOLOGY_IRI, ontologyIRI);
		columnValueMap3.put(OntologyTermIndexRepository.ONTOLOGY_NAME, "test ontology");
		columnValueMap3.put(OntologyTermIndexRepository.LAST, false);
		columnValueMap3.put(OntologyTermIndexRepository.ROOT, false);
		columnValueMap3.put(OntologyTermIndexRepository.NODE_PATH, "1.2.4");
		columnValueMap3.put(OntologyTermIndexRepository.PARENT_NODE_PATH, "1.2");
		columnValueMap3.put(OntologyTermIndexRepository.PARENT_ONTOLOGY_TERM_IRI, ontologyIRI + "#term1");
		columnValueMap3.put(OntologyTermIndexRepository.ONTOLOGY_TERM_IRI, ontologyIRI + "#term3");
		columnValueMap3.put(OntologyTermIndexRepository.ONTOLOGY_TERM, "ontology term 3");
		columnValueMap3.put(OntologyTermIndexRepository.SYNONYMS, "OT-3");
		Hit hit3 = mock(Hit.class);
		when(hit3.getId()).thenReturn("ontology-3");
		when(hit3.getColumnValueMap()).thenReturn(columnValueMap3);

		SearchService searchService = mock(SearchService.class);
		when(
				searchService.search(new SearchRequest(
						AsyncOntologyIndexer.createOntologyTermDocumentType(ontologyIRI), new QueryImpl().eq(
								OntologyTermIndexRepository.ENTITY_TYPE, OntologyTermIndexRepository.TYPE_ONTOLOGYTERM)
								.pageSize(1), null))).thenReturn(new SearchResult(3, Arrays.asList(hit1)));

		when(
				searchService.search(new SearchRequest(
						AsyncOntologyIndexer.createOntologyTermDocumentType(ontologyIRI),
						new QueryImpl().eq(OntologyTermIndexRepository.ENTITY_TYPE,
								OntologyTermIndexRepository.TYPE_ONTOLOGYTERM), null))).thenReturn(
				new SearchResult(3, Arrays.asList(hit1, hit2, hit3)));

		when(
				searchService.search(new SearchRequest(
						AsyncOntologyIndexer.createOntologyTermDocumentType(ontologyIRI), new QueryImpl()
								.eq(OntologyTermIndexRepository.PARENT_NODE_PATH, "1.2")
								.and()
								.eq(OntologyTermIndexRepository.ENTITY_TYPE,
										OntologyTermIndexRepository.TYPE_ONTOLOGYTERM), null))).thenReturn(
				new SearchResult(2, Arrays.asList(hit2, hit3)));

		when(
				searchService.search(new SearchRequest(
						AsyncOntologyIndexer.createOntologyTermDocumentType(ontologyIRI), new QueryImpl()
								.eq(OntologyTermIndexRepository.NODE_PATH, "1.2.3")
								.and()
								.eq(OntologyTermIndexRepository.ENTITY_TYPE,
										OntologyTermIndexRepository.TYPE_ONTOLOGYTERM), null))).thenReturn(
				new SearchResult(1, Arrays.asList(hit2)));

		when(
				searchService.count(AsyncOntologyIndexer.createOntologyTermDocumentType(ontologyIRI), new QueryImpl()
						.pageSize(Integer.MAX_VALUE).offset(Integer.MIN_VALUE))).thenReturn(new Long(3));

		when(searchService.searchById(AsyncOntologyIndexer.createOntologyTermDocumentType(ontologyIRI), "ontology-3"))
				.thenReturn(hit3);

		ontologyTermIndexRepository = new OntologyTermQueryRepository("test-ontology", ontologyIRI, searchService);
	}

	@Test
	public void count()
	{
		assertEquals(ontologyTermIndexRepository.count(new QueryImpl()), 3);
	}

	@Test
	public void findAll()
	{
		for (Entity entity : ontologyTermIndexRepository.findAll(new QueryImpl()))
		{
			Object rootObject = entity.get(OntologyTermIndexRepository.ROOT);
			if (rootObject != null && Boolean.parseBoolean(rootObject.toString()))
			{
				List<String> validOntologyTermIris = Arrays.asList(ontologyIRI + "#term2", ontologyIRI + "#term3");
				for (Entity subEntity : ontologyTermIndexRepository.findAll(new QueryImpl().eq(
						OntologyTermIndexRepository.PARENT_NODE_PATH, entity.get(OntologyTermIndexRepository.NODE_PATH)
								.toString())))
				{
					assertTrue(validOntologyTermIris.contains(subEntity.get(
							OntologyTermIndexRepository.ONTOLOGY_TERM_IRI).toString()));
				}
			}
		}
	}

	@Test
	public void findOneQuery()
	{
		Entity entity = ontologyTermIndexRepository.findOne(new QueryImpl().eq(OntologyTermIndexRepository.NODE_PATH,
				"1.2.3"));
		assertEquals(entity.get(OntologyTermIndexRepository.SYNONYMS).toString(), "OT-2");
	}

	@Test
	public void findOneObject()
	{
		Entity entity = ontologyTermIndexRepository.findOne("ontology-3");
		assertEquals(entity.get(OntologyTermIndexRepository.SYNONYMS).toString(), "OT-3");
	}

	@Test
	public void getUrl()
	{
		assertEquals(ontologyTermIndexRepository.getUrl(),
				"ontologytermindex://" + ontologyTermIndexRepository.getName());
	}
}
