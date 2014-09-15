package org.molgenis.omx.biobankconnect.ontologyservice;

import static org.molgenis.omx.biobankconnect.ontologyservice.OntologyServiceController.URI;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.molgenis.data.Entity;
import org.molgenis.data.UnknownEntityException;
import org.molgenis.data.Writable;
import org.molgenis.data.csv.CsvRepository;
import org.molgenis.data.elasticsearch.util.Hit;
import org.molgenis.data.elasticsearch.util.SearchResult;
import org.molgenis.data.excel.ExcelWriter;
import org.molgenis.data.processor.LowerCaseProcessor;
import org.molgenis.data.rest.EntityCollectionResponse;
import org.molgenis.data.rest.EntityPager;
import org.molgenis.data.support.MapEntity;
import org.molgenis.framework.ui.MolgenisPluginController;
import org.molgenis.util.FileStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(URI)
public class OntologyServiceController extends MolgenisPluginController
{
	@Autowired
	private OntologyService ontologyService;

	@Autowired
	private FileStore fileStore;

	public static final String ID = "ontologyservice";
	public static final String URI = MolgenisPluginController.PLUGIN_URI_PREFIX + ID;
	private String ontologyUrl = null;
	private List<String> inputLines = new ArrayList<String>();

	public OntologyServiceController()
	{
		super(URI);
	}

	@RequestMapping(method = GET)
	public String init(Model model)
	{
		model.addAttribute("ontologies", ontologyService.getAllOntologies());
		return "ontology-match-view";
	}

	@RequestMapping(method = POST, value = "/match")
	public String match(@RequestParam(value = "selectOntologies", required = true) String ontologyUrl,
			@RequestParam(value = "inputTerms", required = true) String inputTerms, Model model)
	{

		if (StringUtils.isEmpty(ontologyUrl) || StringUtils.isEmpty(inputTerms)) return init(model);

		this.ontologyUrl = ontologyUrl;
		this.inputLines = Arrays.asList(inputTerms.split("\n"));
		model.addAttribute("total", inputTerms.split("\n").length);
		model.addAttribute("ontologyUrl", this.ontologyUrl);
		return "ontology-match-view-result";
	}

	@RequestMapping(method = POST, value = "/match/upload", headers = "Content-Type=multipart/form-data")
	public String upload(@RequestParam(value = "selectOntologies", required = true) String ontologyUrl,
			@RequestParam(value = "file", required = true) Part file, Model model) throws IOException
	{
		if (StringUtils.isEmpty(ontologyUrl) || file == null) return init(model);
		this.ontologyUrl = ontologyUrl;
		CsvRepository reader = null;
		try
		{
			File uploadFile = fileStore.store(file.getInputStream(), file.getName() + "_input.txt");
			inputLines = collectAllLinesFromFile(uploadFile);
			model.addAttribute("total", inputLines.size());
		}
		finally
		{
			if (reader != null) IOUtils.closeQuietly(reader);
		}
		return "ontology-match-view-result";
	}

	@RequestMapping(method = GET, value = "/match/download")
	public void download(HttpServletResponse response, Model model) throws IOException
	{
		if (ontologyUrl != null && inputLines != null)
		{
			ExcelWriter excelWriter = null;
			try
			{
				response.setContentType("application/vnd.ms-excel");
				response.addHeader("Content-Disposition", "attachment; filename=" + getCsvFileName("match-result"));
				excelWriter = new ExcelWriter(response.getOutputStream());
				excelWriter.addCellProcessor(new LowerCaseProcessor(true, false));
				int iteration = inputLines.size() / 1000 + 1;
				List<String> columnHeaders = Arrays.asList("InputTerm", "OntologyTerm", "Synonym used for matching",
						"OntologyTermUrl", "OntologyUrl", "CombinedScore", "LuceneScore");
				for (int i = 0; i < iteration; i++)
				{
					Writable sheetWriter = excelWriter.createWritable("result" + (i + 1), columnHeaders);
					int lowerBound = i * 1000;
					int upperBound = (i + 1) * 1000 < inputLines.size() ? (i + 1) * 1000 : inputLines.size();

					for (String term : inputLines.subList(lowerBound, upperBound))
					{
						for (Hit hit : ontologyService.search(ontologyUrl, term).getSearchHits())
						{
							Entity row = new MapEntity();
							row.set("InputTerm", term);
							row.set("OntologyTerm", hit.getColumnValueMap().get("ontologyTerm"));
							row.set("Synonym used for matching", hit.getColumnValueMap().get("ontologyTermSynonym"));
							row.set("OntologyTermUrl", hit.getColumnValueMap().get("ontologyTermIRI"));
							row.set("OntologyUrl", hit.getColumnValueMap().get("ontologyIRI"));
							row.set("CombinedScore", hit.getColumnValueMap().get("combinedScore"));
							row.set("LuceneScore", hit.getColumnValueMap().get("score"));
							sheetWriter.add(row);
						}
					}
				}
			}
			finally
			{
				if (excelWriter != null) IOUtils.closeQuietly(excelWriter);
			}
		}
	}

	@RequestMapping(method = POST, value = "/match/retrieve")
	@ResponseBody
	public EntityCollectionResponse matchResult(@RequestBody EntityPager entityPager)
	{
		if (inputLines == null || inputLines.isEmpty()) throw new UnknownEntityException("The inputTerms is empty!");
		if (StringUtils.isEmpty(ontologyUrl)) throw new UnknownEntityException("The ontologyUrl is empty!");
		List<Map<String, Object>> entities = new ArrayList<Map<String, Object>>();

		int count = inputLines.size();
		int start = entityPager.getStart();
		int num = entityPager.getNum();
		int toIndex = start + num;

		for (String eachLine : inputLines.subList(start, toIndex > count ? count : toIndex))
		{
			Map<String, Object> entity = new HashMap<String, Object>();
			entity.put("term", eachLine);
			entity.put("results", ontologyService.search(ontologyUrl, eachLine));
			entities.add(entity);
		}
		EntityPager pager = new EntityPager(start, num, (long) count, null);
		return new EntityCollectionResponse(pager, entities, "/match/retrieve");
	}

	@RequestMapping(method = POST, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public SearchResult query(@RequestBody OntologyServiceRequest ontologyTermRequest)
	{
		String ontologyUrl = ontologyTermRequest.getOntologyIri();
		String queryString = ontologyTermRequest.getQueryString();
		if (ontologyUrl == null || queryString == null) return new SearchResult(0, Collections.<Hit> emptyList());
		return ontologyService.search(ontologyUrl, queryString);
	}

	private List<String> collectAllLinesFromFile(File uploadFile) throws IOException
	{
		List<String> terms = new ArrayList<String>();
		InputStream inputStream = null;
		BufferedReader bufferedReader = null;
		String line = null;
		try
		{
			inputStream = new FileInputStream(uploadFile);
			bufferedReader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("UTF-8")));
			while ((line = bufferedReader.readLine()) != null)
			{
				if (!StringUtils.isEmpty(line)) terms.add(line.trim());
			}
		}
		finally
		{
			if (bufferedReader != null) bufferedReader.close();
		}
		return terms;
	}

	private String getCsvFileName(String dataSetName)
	{
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return dataSetName + "_" + dateFormat.format(new Date()) + ".xls";
	}
}