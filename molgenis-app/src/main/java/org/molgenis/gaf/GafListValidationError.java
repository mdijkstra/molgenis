package org.molgenis.gaf;

public class GafListValidationError implements Comparable<GafListValidationError>
{
	private final int row;
	private final String colName;
	private final String value;
	private final String msg;

	public GafListValidationError(int row, String colName, String value, String msg)
	{
		this.row = row;
		this.colName = colName;
		this.value = value;
		this.msg = msg;
	}

	public int getRow()
	{
		return row;
	}

	public String getColName()
	{
		return colName;
	}

	public String getValue()
	{
		return value;
	}

	public String getMsg()
	{
		return msg;
	}

	@Override
	public String toString()
	{
		return "row: " + row + "\tcol: " + colName + "\tval: " + value + (msg != null ? "\tmsg: " + msg : "");
	}

	public String toStringHtml()
	{
		return "<tr><td>" + row + "</td><td>" + colName + "</td><td>" + value + "</td><td>" + (msg != null ? msg : "")
				+ "</td></tr>";
	}

	@Override
	public int compareTo(GafListValidationError other)
	{
		final int comparison;
		int thisRow = this.getRow();
		int otherRow = other.getRow();

		if (thisRow < otherRow)
		{
			comparison = -1;
		}
		else if (thisRow > otherRow)
		{
			comparison = 1;
		}
		else
		{
			comparison = 0;
		}

		return comparison;
	}
}
