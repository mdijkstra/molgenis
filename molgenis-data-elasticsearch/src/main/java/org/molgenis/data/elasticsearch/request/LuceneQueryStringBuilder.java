package org.molgenis.data.elasticsearch.request;

import static org.molgenis.data.QueryRule.Operator.EQUALS;
import static org.molgenis.data.QueryRule.Operator.GREATER;
import static org.molgenis.data.QueryRule.Operator.GREATER_EQUAL;
import static org.molgenis.data.QueryRule.Operator.IN;
import static org.molgenis.data.QueryRule.Operator.LESS;
import static org.molgenis.data.QueryRule.Operator.LESS_EQUAL;
import static org.molgenis.data.QueryRule.Operator.LIKE;
import static org.molgenis.data.QueryRule.Operator.NESTED;
import static org.molgenis.data.QueryRule.Operator.NOT;
import static org.molgenis.data.QueryRule.Operator.OR;
import static org.molgenis.data.QueryRule.Operator.SEARCH;

import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.molgenis.data.QueryRule;
import org.molgenis.data.QueryRule.Operator;

import com.google.common.math.DoubleMath;

/**
 * Builds a Lucene query from molgenis QueryRules
 * 
 * @author erwin
 * 
 */
public class LuceneQueryStringBuilder
{
	// The characters that need to be escaped are: && || + - ! ( ) { } [ ] " ~ *
	// / \ ? :
	// ^ was not included because it needs to be used for query boosting
	private static final String LUCENE_ESCAPE_CHARS_VALUE = "[-&+!\\|\\(\\){}\\[\\]\"\\~\\*\\?:\\\\\\/]";
	private static final Pattern LUCENE_PATTERN_VALUE = Pattern.compile(LUCENE_ESCAPE_CHARS_VALUE);

	// Field also needs escaping of whitespace (TODO test each char if it's
	// really needed to be escaped)
	private static final String LUCENE_ESCAPE_CHARS_FIELD = "[-&+!\\|\\(\\){}\\[\\]\\^\"\\~\\*\\?:\\s\\\\]";
	private static final Pattern LUCENE_PATTERN_FIELD = Pattern.compile(LUCENE_ESCAPE_CHARS_FIELD);

	private static final String REPLACEMENT_STRING = "\\\\$0";

	/**
	 * Builds a lucene query string
	 * 
	 * @param queryRules
	 * @return the lucene query
	 */
	@edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE", justification = "False positive for Redundant nullcheck of previousRule")
	public static String buildQueryString(List<QueryRule> queryRules)
	{
		if (queryRules.isEmpty())
		{
			return "*:*";
		}

		StringBuilder sb = new StringBuilder();

		for (QueryRule queryRule : queryRules)
		{
			if (queryRule.getOperator() == NESTED)
			{
				sb.append(" ( ");
				sb.append(buildQueryString(queryRule.getNestedRules()));
				sb.append(" )");
			}
			else if (queryRule.getOperator() == OR)
			{
				sb.append(" " + queryRule.getOperator() + " ");
			}
			else if (queryRule.getOperator() == IN)
			{
				Iterator<?> it = ((Iterable<?>) queryRule.getValue()).iterator();
				while (it.hasNext())
				{
					sb.append(escapeField(queryRule.getField())).append(":").append(getValue(it.next()));
					if (it.hasNext())
					{
						sb.append(" OR ");
					}
				}
			}
			else if (queryRule.getOperator() == EQUALS || queryRule.getOperator() == NOT
					|| queryRule.getOperator() == LIKE || queryRule.getOperator() == LESS
					|| queryRule.getOperator() == LESS_EQUAL || queryRule.getOperator() == GREATER
					|| queryRule.getOperator() == GREATER_EQUAL || queryRule.getOperator() == SEARCH)
			{
				if (queryRule.getOperator() == NOT)
				{
					sb.append("-");
				}
				Object value = getValue(queryRule.getValue());

				if (((value == null) || (value.equals(""))) && (queryRule.getOperator() == Operator.EQUALS))
				{
					sb.append("_missing_:" + queryRule.getField());
				}
				else
				{
					if (value.toString().matches("[\\.\\d]*") || (!queryRule.getOperator().equals(SEARCH)))
					{
						StringBuilder stringTransformer = new StringBuilder();
						value = stringTransformer.append("\"").append(value).append("\"").toString();
					}

					if (queryRule.getField() != null)
					{
						sb.append(escapeField(queryRule.getField())).append(":");
					}

					switch (queryRule.getOperator())
					{
						case EQUALS:
						case NOT:
						{
							sb.append(value);
							break;
						}

						case LIKE:
							sb.append("*").append(value).append("*");
							break;

						case LESS:
							sb.append("{* TO ").append(value).append("}");
							break;

						case LESS_EQUAL:
							sb.append("[* TO ").append(value).append("]");
							break;

						case GREATER:
							sb.append("{").append(value).append(" TO *}");
							break;

						case GREATER_EQUAL:
							sb.append("[").append(value).append(" TO *]");
							break;

						case SEARCH:
							sb.append(value);
							break;

						default:
							throw new IllegalArgumentException("Operator [" + queryRule.getOperator()
									+ "] not supported");
					}
				}
			}
		}

		return sb.toString();
	}

	// Get the value from the QueryRule for use with lucene
	private static Object getValue(Object valueObj)
	{
		Object value = null;
		if (valueObj != null)
		{
			if (valueObj instanceof String)
			{
				value = escapeValue((String) valueObj);
			}
			else if (valueObj instanceof Double)
			{
				// store Double as Integer if integer value of the double is the
				// same as the double
				double doubleValue = ((Double) valueObj).doubleValue();
				value = DoubleMath.isMathematicalInteger(doubleValue) ? Integer.valueOf((int) doubleValue) : valueObj;
			}
			else
			{
				value = valueObj;
			}
		}

		return value;
	}

	/**
	 * Escape a value for use with lucene
	 * 
	 * @param value
	 * @return the escaped value
	 */
	public static String escapeValue(String value)
	{
		return LUCENE_PATTERN_VALUE.matcher(value).replaceAll(REPLACEMENT_STRING);
	}

	/**
	 * Escape a fieldname for use with lucene
	 * 
	 * @param name
	 * @return the escaped fieldname
	 */
	public static String escapeField(String name)
	{
		return LUCENE_PATTERN_FIELD.matcher(name).replaceAll(REPLACEMENT_STRING);
	}

}
