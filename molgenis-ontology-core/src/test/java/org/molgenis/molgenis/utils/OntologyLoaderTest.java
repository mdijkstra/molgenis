package org.molgenis.molgenis.utils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.molgenis.ontology.utils.OntologyLoader;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class OntologyLoaderTest
{
	OntologyLoader loader;

	@BeforeMethod
	public void setUp() throws OWLOntologyCreationException
	{
		URL url = Thread.currentThread().getContextClassLoader().getResource("test-ontology-loader.owl");
		File file = new File(url.getPath());

		loader = new OntologyLoader("ontology-test", file);
	}

	@Test
	public void getChildClass()
	{
		OWLClass childEntity = mock(OWLClass.class);
		when(childEntity.getIRI()).thenReturn(IRI.create("http://harmonization/test/owl/1.0.0#Child"));

		OWLClass parentEntity = mock(OWLClass.class);
		when(parentEntity.getIRI()).thenReturn(IRI.create("http://harmonization/test/owl/1.0.0#Parent"));

		List<OWLClass> topClasses = new ArrayList<OWLClass>(loader.getRootClasses());

		int count = 0;
		for (OWLClass childClass : loader.getChildClass(topClasses.get(0)))
		{
			if (count == 0) assertEquals(childClass.getIRI(), childEntity.getIRI());
			if (count == 1) assertEquals(childClass.getIRI(), parentEntity.getIRI());
			count++;
		}
	}

	@Test
	public void getLabel()
	{
		List<OWLClass> topClasses = new ArrayList<OWLClass>(loader.getRootClasses());
		assertEquals(loader.getLabel(topClasses.get(0)), "Person label test");
	}

	@Test
	public void getOntologyIRI()
	{
		assertEquals(loader.getOntologyIRI(), "http://harmonization/test/owl/1.0.0");
	}

	@Test
	public void getSynonyms()
	{
		List<OWLClass> topClasses = new ArrayList<OWLClass>(loader.getRootClasses());
		Iterator<String> iterator = loader.getSynonyms(topClasses.get(0)).iterator();
		if (iterator.hasNext()) assertEquals(iterator.next(), "People");

	}

	@Test
	public void getTopClasses()
	{
		OWLClass person = mock(OWLClass.class);
		when(person.getIRI()).thenReturn(IRI.create("http://harmonization/test/owl/1.0.0#Person"));
		for (OWLClass cls : loader.getRootClasses())
		{
			assertEquals(cls.getIRI(), person.getIRI());
		}
	}
}