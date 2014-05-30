/*
 * jimple2boogie - Translates Jimple (or Java) Programs to Boogie
 * Copyright (C) 2013 Martin Schaeaeaeaeaeaeaeaeaef and Stephan Arlt
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.joogie.soot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.joogie.GlobalsCache;
import org.joogie.Options;
import org.joogie.errormodel.ExceptionErrorModel;
import org.joogie.util.Log;

import soot.Body;
import soot.BodyTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.toolkits.graph.ExceptionalUnitGraph;
import boogie.declaration.Procedure;
import boogie.enums.BinaryOperator;
import boogie.location.ILocation;
import boogie.statement.IfStatement;
import boogie.statement.Statement;
import boogie.statement.WhileStatement;

/**
 * Boogie Body Transformer
 * 
 * @author schaef
 */
public class SootBodyTransformer extends BodyTransformer {

	/**
	 * C-tor
	 * 
	 * @param report
	 *            Report
	 */
	public SootBodyTransformer() {
	}

	@Override
	@SuppressWarnings("rawtypes")
	protected void internalTransform(Body arg0, String arg1, Map arg2) {
		if (Options.v().hasScope()) {
			SootMethod method = arg0.getMethod();
			SootClass clazz = method.getDeclaringClass();
			String packageName = clazz.getPackageName();

			if (!packageName.startsWith(Options.v().getScope())) {
				return; // ignore current body
			}
		}

		// add method to report
		SootMethod sootMethod = arg0.getMethod();
		Log.debug("METHOD: " + sootMethod);
		// report.addMethod(sootMethod);

		transformStmtList(arg0);
	}

	/**
	 * Transforms a list of statements
	 * 
	 * @param body
	 *            Body
	 */
	private void transformStmtList(Body body) {
		
		SootProcedureInfo procInfo = GlobalsCache.v().lookupProcedure(
				body.getMethod());

		ExceptionalUnitGraph tug = procInfo.getExceptionalUnitGraph();
		Iterator<Unit> stmtIt = tug.iterator();

		// I am not sure if it is important to first compute the tug,
		// but let's play it safe.

		LinkedList<Statement> boogieStatements = new LinkedList<Statement>();

		while (stmtIt.hasNext()) {
			Stmt s = (Stmt) stmtIt.next();
			SootStmtSwitch bss = new SootStmtSwitch(procInfo);
			s.apply(bss);
			LinkedList<Statement> stmts = bss.popAll();
			boogieStatements.addAll(stmts);
			
		}

		// TODO add code to initialize the the $type field of all parameters?
		
		//initialize the exceptional return flag to false
		//this flag is only set to true if an unexpected exception is
		//thrown.
		ILocation loc = procInfo.getProcedureDeclaration().getLocation();
		boogieStatements.addFirst(
			GlobalsCache.v().getPf().mkAssignmentStatement(loc, 
					procInfo.getExceptionalReturnFlag(), 
					GlobalsCache.v().getPf().mkBooleanLiteral(loc, false))
					);

		if (procInfo.getThisReference()!=null) {
			//for non-static procedures we have to assume that .this is non-null
			boogieStatements.addFirst(
					GlobalsCache.v().getPf().mkAssumeStatement(loc, 
								GlobalsCache.v().getPf().mkBinaryExpression(loc, 
										GlobalsCache.v().getPf().getBoolType(), 
										BinaryOperator.COMPNEQ, 
											procInfo.getThisReference(), 
											SootPrelude.v().getNullConstant()))						
						);		
		}
		
		//now create the procedure implementation that combines
		//the signature procInfo and the body.
		Procedure proc = GlobalsCache
				.v()
				.getPf()
				.mkProcedure(
						procInfo.getProcedureDeclaration(),
						boogieStatements.toArray(new Statement[boogieStatements
								.size()]), procInfo.getLocalVariables());
				
		procInfo.setBoogieProcedure(proc);

		
		
		
	}
	
	
}