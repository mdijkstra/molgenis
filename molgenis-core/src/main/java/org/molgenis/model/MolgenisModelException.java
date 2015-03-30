/**
 * File: invengine.db.ResultSet <br>
 * Copyright: Inventory 2000-2006, GBIC 2005, all rights reserved <br>
 * Changelog:
 * <ul>
 * <li>2005-03-21; 1.0.0; RA Scheltema; Creation.
 * <li>2006-04-15; 1.0.0; MA Swertz Created.
 * </ul>
 */

package org.molgenis.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MolgenisModelException extends Exception
{
	private static final long serialVersionUID = -4778664169051832691L;

	private static final Logger LOG = LoggerFactory.getLogger(MolgenisModelException.class);

	public MolgenisModelException(String error)
	{
		super(error);
		LOG.error(error);
	}
}
