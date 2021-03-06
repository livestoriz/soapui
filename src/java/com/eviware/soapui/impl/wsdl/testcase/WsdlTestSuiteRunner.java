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

package com.eviware.soapui.impl.wsdl.testcase;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.impl.wsdl.WsdlTestSuite;
import com.eviware.soapui.impl.wsdl.support.AbstractTestRunner;
import com.eviware.soapui.model.propertyexpansion.DefaultPropertyExpansionContext;
import com.eviware.soapui.model.support.TestRunListenerAdapter;
import com.eviware.soapui.model.testsuite.TestCase;
import com.eviware.soapui.model.testsuite.TestCaseRunContext;
import com.eviware.soapui.model.testsuite.TestCaseRunner;
import com.eviware.soapui.model.testsuite.TestRunListener;
import com.eviware.soapui.model.testsuite.TestSuite;
import com.eviware.soapui.model.testsuite.TestSuite.TestSuiteRunType;
import com.eviware.soapui.model.testsuite.TestSuiteRunListener;
import com.eviware.soapui.model.testsuite.TestSuiteRunner;
import com.eviware.soapui.support.types.StringToObjectMap;

/**
 * WSDL TestCase Runner - runs all steps in a testcase and collects performance
 * data
 * 
 * @author Ole.Matzura
 */

public class WsdlTestSuiteRunner extends AbstractTestRunner<WsdlTestSuite, WsdlTestSuiteRunContext> implements
		TestSuiteRunner
{
	private TestSuiteRunListener[] listeners;
	private Set<TestCaseRunner> finishedRunners = new HashSet<TestCaseRunner>();
	private Set<TestCaseRunner> activeRunners = new HashSet<TestCaseRunner>();
	private int currentTestCaseIndex;
	private WsdlTestCase currentTestCase;
	private TestRunListener parallellTestRunListener = new ParallellTestRunListener();

	public WsdlTestSuiteRunner( WsdlTestSuite testSuite, StringToObjectMap properties )
	{
		super( testSuite, properties );
	}

	public WsdlTestSuiteRunContext createContext( StringToObjectMap properties )
	{
		return new WsdlTestSuiteRunContext( this, properties );
	}

	public void onCancel( String reason )
	{
		for( TestCaseRunner runner : activeRunners.toArray( new TestCaseRunner[activeRunners.size()] ) )
		{
			runner.cancel( reason );
		}
	}

	public void onFail( String reason )
	{
		for( TestCaseRunner runner : activeRunners.toArray( new TestCaseRunner[activeRunners.size()] ) )
		{
			runner.fail( reason );
		}
	}

	public void internalRun( WsdlTestSuiteRunContext runContext ) throws Exception
	{
		WsdlTestSuite testSuite = getTestRunnable();

		listeners = testSuite.getTestSuiteRunListeners();
		testSuite.runSetupScript( runContext, this );
		if( !isRunning() )
			return;

		if( testSuite.getTimeout() > 0 )
		{
			startTimeoutTimer( testSuite.getTimeout() );
		}

		notifyBeforeRun();
		if( !isRunning() )
			return;

		if( testSuite.getRunType() == TestSuiteRunType.SEQUENTIAL )
		{
			runSequential( testSuite, runContext );
		}
		else if( testSuite.getRunType() == TestSuiteRunType.PARALLEL )
		{
			runParallel( testSuite, runContext );
		}
	}

	private void runParallel( WsdlTestSuite testSuite, WsdlTestSuiteRunContext runContext )
	{
		currentTestCaseIndex = -1;
		currentTestCase = null;

		for( TestCase testCase : testSuite.getTestCaseList() )
		{
			if( !testCase.isDisabled() )
			{
				testCase.addTestRunListener( parallellTestRunListener );
				notifyBeforeRunTestCase( testCase );
				runTestCase( ( WsdlTestCase )testCase, true );
			}
		}

		try
		{
			synchronized( activeRunners )
			{
				activeRunners.wait();
			}
		}
		catch( InterruptedException e )
		{
			e.printStackTrace();
		}
	}

	private void runSequential( WsdlTestSuite testSuite, WsdlTestSuiteRunContext runContext )
	{
		currentTestCaseIndex = 0;
		for( ; isRunning() && currentTestCaseIndex < testSuite.getTestCaseCount(); currentTestCaseIndex++ )
		{
			currentTestCase = testSuite.getTestCaseAt( currentTestCaseIndex );
			if( !currentTestCase.isDisabled() )
			{
				notifyBeforeRunTestCase( currentTestCase );
				TestCaseRunner testCaseRunner = runTestCase( currentTestCase, false );
				activeRunners.remove( testCaseRunner );
				finishedRunners.add( testCaseRunner );
				notifyAfterRunTestCase( testCaseRunner );
			}
		}

		updateStatus();
	}

	private void updateStatus()
	{
		for( TestCaseRunner runner : finishedRunners )
		{
			if( runner.getStatus() == Status.FAILED )
			{
				setStatus( Status.FAILED );
				break;
			}
		}
	}

	private TestCaseRunner runTestCase( WsdlTestCase testCaseAt, boolean async )
	{
		DefaultPropertyExpansionContext properties = ( DefaultPropertyExpansionContext )getRunContext().getProperties();
		properties.put( "#TestSuiteRunner#", this );

		TestCaseRunner currentRunner = testCaseAt.run( properties, true );
		activeRunners.add( currentRunner );
		if( !async )
			currentRunner.waitUntilFinished();

		return currentRunner;
	}

	protected void internalFinally( WsdlTestSuiteRunContext runContext )
	{
		WsdlTestSuite testSuite = getTestRunnable();

		try
		{
			testSuite.runTearDownScript( runContext, this );
		}
		catch( Exception e )
		{
			SoapUI.logError( e );
		}

		notifyAfterRun();

		runContext.clear();
		listeners = null;
	}

	private void notifyAfterRun()
	{
		if( listeners == null || listeners.length == 0 )
			return;

		for( int i = 0; i < listeners.length; i++ )
		{
			listeners[i].afterRun( this, getRunContext() );
		}
	}

	private void notifyBeforeRun()
	{
		if( listeners == null || listeners.length == 0 )
			return;

		for( int i = 0; i < listeners.length; i++ )
		{
			listeners[i].beforeRun( this, getRunContext() );
		}
	}

	private void notifyAfterRunTestCase( TestCaseRunner testCaseRunner )
	{
		if( listeners == null || listeners.length == 0 )
			return;

		for( int i = 0; i < listeners.length; i++ )
		{
			listeners[i].afterTestCase( this, getRunContext(), testCaseRunner );
		}
	}

	private void notifyBeforeRunTestCase( TestCase testCase )
	{
		if( listeners == null || listeners.length == 0 )
			return;

		for( int i = 0; i < listeners.length; i++ )
		{
			listeners[i].beforeTestCase( this, getRunContext(), testCase );
		}
	}

	public TestSuite getTestSuite()
	{
		return getTestRunnable();
	}

	public List<TestCaseRunner> getResults()
	{
		return Arrays.asList( finishedRunners.toArray( new TestCaseRunner[finishedRunners.size()] ) );
	}

	public int getCurrentTestCaseIndex()
	{
		return currentTestCaseIndex;
	}

	public WsdlTestCase getCurrentTestCase()
	{
		return currentTestCase;
	}

	private class ParallellTestRunListener extends TestRunListenerAdapter
	{
		@Override
		public void afterRun( TestCaseRunner testRunner, TestCaseRunContext runContext )
		{
			notifyAfterRunTestCase( testRunner );

			activeRunners.remove( testRunner );
			finishedRunners.add( testRunner );

			testRunner.getTestCase().removeTestRunListener( parallellTestRunListener );

			if( activeRunners.isEmpty() )
			{
				updateStatus();

				synchronized( activeRunners )
				{
					activeRunners.notify();
				}
			}
		}
	}
}
