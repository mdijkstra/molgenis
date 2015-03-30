package org.molgenis;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ArrayUtils;
import org.molgenis.fieldtypes.BoolField;
import org.molgenis.fieldtypes.DateField;
import org.molgenis.fieldtypes.DatetimeField;
import org.molgenis.fieldtypes.DecimalField;
import org.molgenis.fieldtypes.EnumField;
import org.molgenis.fieldtypes.FileField;
import org.molgenis.fieldtypes.HyperlinkField;
import org.molgenis.fieldtypes.ImageField;
import org.molgenis.fieldtypes.IntField;
import org.molgenis.fieldtypes.LongField;
import org.molgenis.fieldtypes.MrefField;
import org.molgenis.fieldtypes.StringField;
import org.molgenis.fieldtypes.TextField;
import org.molgenis.fieldtypes.XrefField;
import org.molgenis.generators.DataTypeGen;
import org.molgenis.generators.EntityMetaDataGen;
import org.molgenis.generators.Generator;
import org.molgenis.generators.JpaRepositoryGen;
import org.molgenis.generators.JpaRepositorySourceGen;
import org.molgenis.generators.db.DatabaseConfigGen;
import org.molgenis.generators.db.EntitiesImporterGen;
import org.molgenis.generators.db.EntitiesValidatorGen;
import org.molgenis.generators.db.JDBCMetaDatabaseGen;
import org.molgenis.generators.db.PersistenceGen;
import org.molgenis.model.MolgenisModel;
import org.molgenis.model.elements.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * MOLGENIS generator. Run this to fire up all the generators. Optionally add {@link org.molgenis.MolgenisOptions}
 * 
 * @author Morris Swertz
 */
public class Molgenis
{
	private static final Logger LOG = LoggerFactory.getLogger(Molgenis.class);

	public static void main(String[] args)
	{
		try
		{
			if (args.length == 2)
			{
				new Molgenis(args[0], args[1]).generate();
			}
			else if (args.length == 3)
			{
				if (args[2].equals("--generatetests"))
				{
					new Molgenis(args[0], args[1]).generateTests();
				}
				else
				{
					throw new Exception("Bad second argument: use either --updatedb or --updatedbfillmeta");
				}
			}
			else
			{
				throw new Exception(
						"You have to provide the molgenis.properties file as first argument to generate Molgenis.\n"
								+ "Alternatively, add the additional argument --updatedb OR --updatedbfillmeta to perform the update database action.\n"
								+ "The --updatedbfillmeta will also insert the metadata into the database.\n"
								+ "Your arguments:\n" + Arrays.toString(args));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	MolgenisOptions options = null;
	Model model = null;
	List<Generator> generators = new ArrayList<Generator>();

	public List<Generator> getGenerators()
	{
		return generators;
	}

	public void setGenerators(List<Generator> generators)
	{
		this.generators = generators;
	}

	public Molgenis(String propertiesFile, Class<? extends Generator>... generatorsToUse) throws Exception
	{
		this(new MolgenisOptions(propertiesFile), null, generatorsToUse);
	}

	public Molgenis(String propertiesFile, String outputPath, Class<? extends Generator>... generatorsToUse)
			throws Exception
	{
		this(new MolgenisOptions(propertiesFile), outputPath, generatorsToUse);
	}

	@SuppressWarnings("unchecked")
	public Molgenis(String propertiesFile) throws Exception
	{
		this(new MolgenisOptions(propertiesFile), null, new Class[]
		{});
	}

	@SuppressWarnings("unchecked")
	public Molgenis(String propertiesFile, String outputPath) throws Exception
	{
		this(new MolgenisOptions(propertiesFile), outputPath, new Class[]
		{});
	}

	public Molgenis()
	{
	}

	public void init(String propertiesFile, String outputPath, Class<? extends Generator>... generatorsToUse)
			throws Exception
	{
		new Molgenis(new MolgenisOptions(propertiesFile), outputPath, generatorsToUse);
	}

	public <E extends Generator> Molgenis(MolgenisOptions options, Class<? extends Generator>... generatorsToUse)
			throws Exception
	{
		this(options, null, generatorsToUse);
	}

	/**
	 * Construct a MOLGENIS generator
	 * 
	 * @param options
	 *            with generator settings
	 * @param generatorsToUse
	 *            optional list of generator classes to include
	 * @throws Exception
	 */
	public <E extends Generator> Molgenis(MolgenisOptions options, String outputPath,
			Class<? extends Generator>... generatorsToUse) throws Exception
	{
		this.loadFieldTypes();

		this.options = options;

		LOG.debug("\nMOLGENIS version " + org.molgenis.Version.convertToString());
		LOG.debug("working dir: " + System.getProperty("user.dir"));

		// clean options
		if (outputPath != null)
		{
			// workaround for java string escaping bug in freemarker (see
			// UsedMolgenisOptionsGen.ftl)
			outputPath = outputPath.replace('\\', '/');
			if (!outputPath.endsWith("/")) outputPath = outputPath + "/";
		}

		options.output_src = outputPath != null ? outputPath + options.output_src : options.output_src;
		if (!options.output_src.endsWith("/")) options.output_src = options.output_src.endsWith("/") + "/";
		options.output_hand = outputPath != null ? outputPath + options.output_hand : options.output_hand;
		if (!options.output_hand.endsWith("/")) options.output_hand = options.output_hand + "/";
		options.output_web = outputPath != null ? outputPath + options.output_web : options.output_web;
		if (!options.output_web.endsWith("/")) options.output_web = options.output_web + "/";
		options.output_doc = outputPath != null ? outputPath + options.output_doc : options.output_doc;
		if (!options.output_doc.endsWith("/")) options.output_doc = options.output_doc + "/";

		if (options.generate_jpa)
		{
			if (options.generate_db)
			{
				generators.add(new DatabaseConfigGen());
			}

			generators.add(new DataTypeGen());
			generators.add(new EntityMetaDataGen());
			generators.add(new JpaRepositoryGen());
			generators.add(new JDBCMetaDatabaseGen());

			if (options.generate_persistence)
			{
				generators.add(new PersistenceGen());
			}

			if (options.generate_jpa_repository_source)
			{
				generators.add(new JpaRepositorySourceGen());
			}
		}
		else
		{
			LOG.warn("SEVERE: Skipping ALL SQL ....");
		}

		if (options.generate_entityio)
		{
			generators.add(new EntitiesImporterGen());
			generators.add(new EntitiesValidatorGen());
		}

		// clean out generators
		List<Generator> use = new ArrayList<Generator>();
		if (!ArrayUtils.isEmpty(generatorsToUse))
		{
			for (Class<? extends Generator> c : generatorsToUse)
			{
				use.add(c.newInstance());
			}
			generators = use;
		}

		LOG.debug("\nUsing generators:\n" + toString());

		// parsing model
		model = MolgenisModel.parse(options);
	}

	private void loadFieldTypes()
	{
		MolgenisFieldTypes.addType(new BoolField());
		MolgenisFieldTypes.addType(new DateField());
		MolgenisFieldTypes.addType(new DatetimeField());
		MolgenisFieldTypes.addType(new DecimalField());
		MolgenisFieldTypes.addType(new EnumField());
		MolgenisFieldTypes.addType(new FileField());
		MolgenisFieldTypes.addType(new ImageField());
		MolgenisFieldTypes.addType(new HyperlinkField());
		MolgenisFieldTypes.addType(new LongField());
		MolgenisFieldTypes.addType(new MrefField());
		MolgenisFieldTypes.addType(new StringField());
		MolgenisFieldTypes.addType(new TextField());
		MolgenisFieldTypes.addType(new XrefField());
		MolgenisFieldTypes.addType(new IntField());
	}

	public void generateTests() throws Exception
	{
		options.setGenerateTests(true);
		generate();
	}

	/**
	 * Apply all generators on the model
	 * 
	 * @param model
	 */
	public void generate() throws Exception
	{
		LOG.info("Generating ...");
		LOG.debug("\nUsing options:\n" + options.toString());

		File generatedFolder = new File(options.output_dir);
		if (generatedFolder.exists() && options.delete_generated_folder)
		{
			LOG.debug("removing previous generated folder " + generatedFolder);
			deleteContentOfDirectory(generatedFolder);
			deleteContentOfDirectory(new File(options.output_src));
			deleteContentOfDirectory(new File(options.output_sql));
		}

		final int nrCores = Runtime.getRuntime().availableProcessors();
		ExecutorService executorService = Executors.newFixedThreadPool(nrCores);
		try
		{
			executorService.invokeAll(Lists.transform(generators, new Function<Generator, Callable<Boolean>>()
			{
				@Override
				@Nullable
				public Callable<Boolean> apply(@Nullable final Generator generator)
				{
					return generator != null ? new Callable<Boolean>()
					{

						@Override
						public Boolean call() throws Exception
						{
							generator.generate(model, options);
							return true;
						}
					} : null;
				}
			}));
		}
		finally
		{
			executorService.shutdown();
		}

		LOG.info("Generation completed at " + new Date());
	}

	/**
	 * Deletes the content of directory (path), excluding hidden files like .svn
	 * 
	 * @param path
	 *            of directory to delete
	 * @return if and only if the content of directory (path) is successfully deleted; false otherwise
	 */
	static public boolean deleteContentOfDirectory(File path)
	{
		boolean result = true;
		if (path.exists())
		{
			File[] files = path.listFiles();
			for (File f : files)
			{
				if (!f.isHidden())
				{
					if (f.isDirectory())
					{
						result &= deleteContentOfDirectory(f);
						boolean ok = f.delete();
						if (!ok) LOG.warn("file delete failed: " + f.getName());
					}
					else
					{
						result &= f.delete();
					}
				}
			}
		}
		return result;

	}

	/**
	 * Report current settings of the generator.
	 */
	@Override
	public final String toString()
	{
		StringBuffer result = new StringBuffer();

		// get name, description and padding
		Map<String, String> map = new LinkedHashMap<String, String>();
		int padding = 0;
		for (Generator g : generators)
		{
			// get the name (without common path)
			String generatorName = null;
			if (g.getClass().getName().indexOf(this.getClass().getPackage().getName()) == 0)
			{
				generatorName = g.getClass().getName().substring(this.getClass().getPackage().getName().length() + 1);
			}
			else
			{
				generatorName = g.getClass().getName();
			}

			// calculate the padding
			padding = Math.max(padding, generatorName.length());

			// add to map
			map.put(generatorName, g.getDescription());
		}

		// print
		for (Map.Entry<String, String> entry : map.entrySet())
		{
			// create padding
			String spaces = "";
			for (int i = entry.getKey().toString().length(); i < padding; i++)
			{
				spaces += " ";
			}
			result.append(entry.getKey() + spaces + " #" + entry.getValue() + "\n");
		}
		return result.toString();
	}

	public MolgenisOptions getMolgenisOptions()
	{
		return this.options;
	}
}