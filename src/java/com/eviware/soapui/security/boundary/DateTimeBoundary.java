/*
 *  soapUI, copyright (C) 2004-2011 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */
package com.eviware.soapui.security.boundary;

import java.text.SimpleDateFormat;

/**
 * @author nebojsa.tasic
 */
public class DateTimeBoundary extends AbstractBoundary
{

	private static final int OFFSET = 10;
	public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
	public static SimpleDateFormat simpleDateFormat = new SimpleDateFormat( DATE_FORMAT );

	@Override
	public String outOfBoundary( int restrictionAttribute, String value )
	{
		switch( restrictionAttribute )
		{
		case MAX_EXCLISIVE :
			return BoundaryUtils.createDate( value, ( int )( Math.random() * OFFSET ), simpleDateFormat );
		case MIN_EXCLISIVE :
			return BoundaryUtils.createDate( value, -( int )( Math.random() * OFFSET ), simpleDateFormat );
		case MAX_INCLISIVE :
			return BoundaryUtils.createDate( value, ( int )( Math.random() * OFFSET + 1 ), simpleDateFormat );
		case MIN_INCLISIVE :
			return BoundaryUtils.createDate( value, ( -( int )( Math.random() * OFFSET ) - 1 ), simpleDateFormat );
		default :
			return null;
		}
	}

}
