/*
 * jimple2boogie - Translates Jimple (or Java) Programs to Boogie
 * Copyright (C) 2013 Martin Schaeaeaeaeaeaeaeaeaeaeaeaeaeaeaeaeaeaeaeaeaeaeaeaeaeaeaef and Stephan Arlt
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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.joogie.GlobalsCache;
import org.joogie.errormodel.AbstractErrorModel;
import org.joogie.errormodel.AssertionErrorModel;
import org.joogie.errormodel.ExceptionErrorModel;
import org.joogie.util.Log;
import org.joogie.util.TranslationHelpers;
import org.joogie.util.Util;

import soot.ArrayType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.BreakpointStmt;
import soot.jimple.CaughtExceptionRef;
import soot.jimple.EnterMonitorStmt;
import soot.jimple.ExitMonitorStmt;
import soot.jimple.FieldRef;
import soot.jimple.GotoStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.IfStmt;
import soot.jimple.InterfaceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.LookupSwitchStmt;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NewMultiArrayExpr;
import soot.jimple.NopStmt;
import soot.jimple.RetStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StmtSwitch;
import soot.jimple.StringConstant;
import soot.jimple.TableSwitchStmt;
import soot.jimple.ThrowStmt;
import soot.jimple.VirtualInvokeExpr;
import soot.toolkits.graph.ExceptionalUnitGraph.ExceptionDest;
import boogie.ProgramFactory;
import boogie.ast.VarList;
import boogie.enums.BinaryOperator;
import boogie.enums.UnaryOperator;
import boogie.expression.ArrayAccessExpression;
import boogie.expression.Expression;
import boogie.expression.IdentifierExpression;
import boogie.location.ILocation;
import boogie.specification.EnsuresSpecification;
import boogie.specification.RequiresSpecification;
import boogie.specification.Specification;
import boogie.statement.Statement;

/**
 * @author schaef
 */
public class SootStmtSwitch implements StmtSwitch {

	private SootProcedureInfo procInfo;
	private SootValueSwitch valueswitch;
	private ProgramFactory pf;
	private Stmt currentStatement = null; //needed to identify throw targets of expressions
	private AbstractErrorModel errorModel;
	
	//used to track Java string constants
	//HashMap<StringConstant, Expression> stringConstantMap = new HashMap<StringConstant, Expression>();
	
	public SootStmtSwitch(SootProcedureInfo pinfo) {
		this.procInfo = pinfo;
		this.pf = GlobalsCache.v().getPf();
		this.valueswitch = new SootValueSwitch(this.procInfo, this);
		if (!org.joogie.Options.v().isExceptionErrorModel()) {
			this.errorModel = new ExceptionErrorModel(this.procInfo, this);
		} else {
			this.errorModel = new AssertionErrorModel(this.procInfo, this);
		}
	}

	public LinkedList<Statement> popAll() {
		LinkedList<Statement> ret = new LinkedList<Statement>();
		ret.addAll(this.boogieStatements);
		this.boogieStatements.clear();
		return ret;
	}

	public AbstractErrorModel getErrorModel() {
		return this.errorModel;
	}
	
	private LinkedList<Statement> boogieStatements = new LinkedList<Statement>();

	/**
	 * this should only be used by the SootValueSwitch if extra guards have to
	 * be created
	 * 
	 * @param guard
	 */
	public void addGuardStatement(Statement guard) {
		this.boogieStatements.add(guard);
	}

	public Stmt getCurrentStatement() {
		return this.currentStatement;
	}
	
	private void injectLabelStatements(Stmt arg0) {
		this.currentStatement = arg0;
		ILocation loc = TranslationHelpers.translateLocation(arg0.getTags());
		if (arg0.getBoxesPointingToThis().size() > 0) {
			this.boogieStatements.add(this.pf.mkLabel(loc, GlobalsCache.v()
					.getUnitLabel(arg0)));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see soot.jimple.StmtSwitch#caseAssignStmt(soot.jimple.AssignStmt)
	 */
	@Override
	public void caseAssignStmt(AssignStmt arg0) {
		injectLabelStatements(arg0);
		ILocation loc = TranslationHelpers.translateLocation(arg0.getTags());
		translateAssignment(loc, arg0.getLeftOp(), arg0.getRightOp(), arg0);
	}

	
	
	public IdentifierExpression createAllocatedVariable(ILocation loc, Type sootType) {
		// create fresh local variable for "right"
		IdentifierExpression newexpr = this.procInfo.createLocalVariable(SootPrelude.v().getReferenceType()); 
		// havoc right
		this.boogieStatements.add(this.pf.mkHavocStatement(loc, newexpr));
		// assume $heap[right, $alloc] == false
		this.boogieStatements
				.add(this.pf.mkAssumeStatement(loc, this.pf
						.mkUnaryExpression(loc, this.pf.getBoolType(),
								UnaryOperator.LOGICNEG,
								this.valueswitch.makeHeapAccessExpression(
										loc, newexpr, SootPrelude.v()
												.getFieldAllocVariable(), false))));
		// $heap[right, $alloc] := true
		translateAssignment(loc, this.valueswitch.makeHeapAccessExpression(
				loc, newexpr, SootPrelude.v().getFieldAllocVariable(), false),
				this.pf.mkBooleanLiteral(loc, true));
		
		// $heap[right, $type] := ...the appropriate type...		
		Expression typeRhs;
		if (sootType instanceof RefType) {
		  typeRhs = GlobalsCache.v().lookupClassVariable(((RefType)sootType).getSootClass());
		  if (typeRhs==null) {
			  throw new RuntimeException("Not a class variable: "+ ((RefType)sootType).getSootClass());
		  }
		} else if (sootType instanceof ArrayType) {
		  typeRhs = GlobalsCache.v().lookupArrayType((ArrayType)sootType);
		  if (typeRhs==null) {
			  throw new RuntimeException("Not a type: "+ (ArrayType)sootType);
		  }
		  
		} else {
		  throw new RuntimeException("Translation of Array Access failed!");
		}
		
		this.boogieStatements.add(this.pf.mkAssumeStatement(loc, 
				this.pf.mkBinaryExpression(loc, this.pf.getBoolType(), BinaryOperator.COMPNEQ, newexpr, 
						SootPrelude.v().getNullConstant())));
		
		translateAssignment(
				loc,
				this.valueswitch.getClassTypeFromExpression(newexpr, false),
				typeRhs);
		return newexpr;		
	}
	
	
	private void translateAssignment(ILocation loc, Value lhs, Value rhs,
			Unit statement) {		
		
		if (rhs instanceof InvokeExpr) {
			InvokeExpr ivk = (InvokeExpr) rhs;
			translateInvokeAssignment(loc, lhs, ivk, statement);
			return;
		}
		lhs.apply(this.valueswitch);
		Expression left = this.valueswitch.getExpression();

		Expression right;
		if (rhs instanceof NewExpr) {
			right = createAllocatedVariable(loc,((NewExpr) rhs).getBaseType());
		} else if (rhs instanceof NewArrayExpr) {
			NewArrayExpr nae = (NewArrayExpr) rhs;
            right = createAllocatedVariable(loc,nae.getType());
			nae.getSize().apply(this.valueswitch);
			Expression sizeexp = this.valueswitch.getExpression();
			// add the size expression.
			this.boogieStatements.add(GlobalsCache.v().setArraySizeStatement(
					right, sizeexp));
		} else if (rhs instanceof NewMultiArrayExpr) {
			// TODO use this.pf.mkAssignmentStatement(loc, this.procInfo.getExceptionVariable(), exceptionvar)
			NewMultiArrayExpr nmae = (NewMultiArrayExpr) rhs;
			for (int i = 0; i < nmae.getSizeCount(); i++) {
				nmae.getSize(i).apply(this.valueswitch);
				Expression sizeexp = this.valueswitch.getExpression();
				// TODO
			}
			right = GlobalsCache.v().makeFreshGlobal(
					SootPrelude.v().getReferenceType(), true, true);
		} else if (rhs instanceof StringConstant) {
			StringConstant str = (StringConstant)rhs;	
	
			//if (!this.stringConstantMap.containsKey(str)) {
				//this.stringConstantMap.put(str, GlobalsCache.createDummyExpression(SootPrelude.v().getReferenceType()));				
			//}
			//TODO: ensure that the dummy var is of appropriate type.
			
			//right = this.stringConstantMap.get(str);
			right = createAllocatedVariable(loc, rhs.getType());
			
			Expression[] indices = {right};
			//assign the size of the string to the appropriate field in the
			//$stringSizeHeapVariable array.
			translateAssignment(loc,
					SootPrelude.v().getStringSizeHeapVariable(),
					this.pf.mkArrayStoreExpression(loc, SootPrelude.v().getStringSizeHeapVariable().getType(), 
							SootPrelude.v().getStringSizeHeapVariable(), indices, 
							this.pf.mkIntLiteral(loc, (new Integer(str.value.length())).toString())
							)
					);
		} else {
			rhs.apply(this.valueswitch);
			right = this.valueswitch.getExpression();
		}

		translateAssignment(loc, left, right);
		//If lhs was a field annotated with @NonNull, we have to ensure that it has not been assigned
		//to null.... maybe this is a hack.
		if (lhs instanceof FieldRef) {
			//TODO: this should not be here ... NonNull types should be encoded in Boogie and later be 
			//checked by the prover or something.
			LinkedList<SootAnnotations.Annotation> annot = SootAnnotations.parseFieldTags(((FieldRef)lhs).getField());
			if (annot.contains(SootAnnotations.Annotation.NonNull)) {
				this.errorModel.createNonNullViolationException(left);
			}			
		} 		
	}

	/**
	 * This method creates an assignment. It is used by caseAssignStmt and
	 * caseIdentityStmt
	 * 
	 * @param loc
	 * @param left
	 * @param right
	 */
	private void translateAssignment(ILocation loc, Expression left,
			Expression right) {
		if (left instanceof IdentifierExpression) {
			this.boogieStatements.add(this.pf.mkAssignmentStatement(loc,
					(IdentifierExpression) left,
					TranslationHelpers.castBoogieTypes(right, left.getType())));
		} else if (left instanceof ArrayAccessExpression) {			
			ArrayAccessExpression aae = (ArrayAccessExpression) left;
			Expression arraystore = this.pf.mkArrayStoreExpression(loc, aae
					.getArray().getType(), aae.getArray(), aae.getIndices(),
					right);
			translateAssignment(loc, aae.getArray(), arraystore);
		} else {
			throw new RuntimeException("Unknown LHS type: "
					+ ((left == null) ? "null" : left.getClass()));
		}
	}

	
	
	/**
	 * create a CfgCallStatement
	 * @param m the procedure to be called
	 * @param throwsclauses a list that contains where the possible exceptions of m a added to. this is needed later.
	 * @param lefts the left hand side of the call that receives the return values
	 */
	private LinkedList<Statement> createCallStatement(ILocation loc, SootMethod m, List<SootClass> throwsclauses, List<IdentifierExpression> lefts, Expression[] args) {
		//we have to clone the lefts because we may add thing to it here.
		List<IdentifierExpression> lefts_clone = new LinkedList<IdentifierExpression>();
		for (IdentifierExpression ide : lefts) {
			lefts_clone.add(ide);
		}
		
		SootProcedureInfo calleeInfo = GlobalsCache.v().lookupProcedure(m);
		
		mergeThrowsClauses(throwsclauses, calleeInfo.getThrowsClasses());
		
		if (calleeInfo.getReturnVariable() != null && lefts_clone.size() == 0) {
			lefts_clone.add(this.procInfo.createLocalVariable(calleeInfo
					.getReturnVariable().getType()));
		}
		/*
		 * now add a fake local if the callee may throw an exception
		 */
		if (calleeInfo.getExceptionVariable() != null) {
			lefts_clone.add(this.procInfo.getExceptionVariable());
		}		

		HashMap<String, Expression> substitutes = new HashMap<String, Expression>();
		for (int i=0; i<calleeInfo.getProcedureDeclaration().getInParams().length; i++) {
			VarList vl = calleeInfo.getProcedureDeclaration().getInParams()[i];
			Expression arg = args[i];
			if (vl.getIdentifiers().length!=1) {
				throw new RuntimeException("That aint right!");
			}
			substitutes.put(vl.getIdentifiers()[0], arg);			
		}
		
		for (Specification spec : calleeInfo.getProcedureDeclaration().getSpecification()) {
			if (spec instanceof RequiresSpecification) {
				this.errorModel.createPreconditionViolationException(
						((RequiresSpecification)spec)
							.getFormula()
							.substitute(substitutes)
						);
			}
		}
		
		
		Statement s = this.pf.mkCallStatement(loc, false,
				lefts_clone.toArray(new IdentifierExpression[lefts_clone.size()]),
				calleeInfo.getBoogieName(), args);
		
		LinkedList<Statement> stmts = new LinkedList<Statement>();
		stmts.add(s);


		//TODO nonNull return hack!
		//this should be removed ... this has to be done on the Boogie side!
		if (calleeInfo.nonNullReturn && lefts.size()>=1) {
			IdentifierExpression returnTarget = lefts.get(0);
			stmts.add(this.errorModel.createAssumeNonNull(returnTarget));
			
			if (calleeInfo.returnTypeVariable!=null) {
				Expression ltype = this.valueswitch.getClassTypeFromExpression(returnTarget, false);				
				stmts.add(GlobalsCache.v().assumeSubType(ltype, calleeInfo.returnTypeVariable));
				if (calleeInfo.exactReturnType) {
					stmts.add(GlobalsCache.v().assumeSubType(calleeInfo.returnTypeVariable, ltype));
				}
				
			}			
		}
		
		return stmts;		
	}

	
	private boolean findPossibleCalledMethods(ILocation loc, SootMethod m, SootClass c, List<SootClass> throwsclauses, List<IdentifierExpression> lefts, Expression[] args) {
				
		//first find all possible subtypes of c
		Collection possibleClasses = null;		
		if (c.isInterface()) {
			possibleClasses = Scene.v().getFastHierarchy().getAllImplementersOfInterface(c);
		} else {
			possibleClasses = Scene.v().getFastHierarchy().getSubclassesOf(c);
		}
		
		boolean mayBeVirtual = possibleClasses != null && !possibleClasses.isEmpty(); 
		
		boolean possiblyOverloaded = false;
		//iterate recursively over the subtypes of c
		
		if (mayBeVirtual) {
			for (Object o : possibleClasses) {
				SootClass child = (SootClass)o;
				//check if at least one subtype implements this function
				possiblyOverloaded = 
						possiblyOverloaded || findPossibleCalledMethods(loc, m, child, throwsclauses, lefts, args);
			}
		}
		
		if (!m.isAbstract() && !m.isStatic() ) {
			
			//System.err.println("Method: "+m.getName() + "\t"+c.getName() );
			
			//arg[0] always stores the $this pointer for the call.
			//I.e., for a.foo() arg[0] contains "a"
			// this.valueswitch.getExprssionJavaClass(args[0]) then returns the
			//type variable for "a".
			Expression typeOfBase = this.valueswitch.getClassTypeFromExpression(args[0], false);
			Expression typeOfCurrentMethod = GlobalsCache.v().lookupClassVariable(c);		
			
			List<Statement> tmp = createCallStatement(loc, m, throwsclauses, lefts, args);
			
			Statement[] call = tmp.toArray(new Statement[tmp.size()]);
			Statement ite = this.pf.mkIfStatement(loc, 
					GlobalsCache.v().sameTypeExpression(typeOfBase, typeOfCurrentMethod), 
					call,
					new Statement[0]);
			this.boogieStatements.add(ite);
			return true;
		} else {
			//System.err.println("Sig: "+m.getSignature() + "\t "+c.getName()+ "abstract "+m.isAbstract() + ", static "+m.isStatic() + ", virtual "+mayBeVirtual );
		}
		
		
		return possiblyOverloaded;
	}
	/**
	 * TODO: this one is not finished
	 * @param ivk
	 */
	private void findPossibleCalledMethods(ILocation loc, InvokeExpr ivk, List<SootClass> throwsclauses, List<IdentifierExpression> lefts, Expression[] args) {
		SootClass c = null;
		
		//TODO: combine with getCalleeInstance once this works
		if (ivk instanceof InterfaceInvokeExpr) {
			//TODO: error model
			//inheritance model
			InterfaceInvokeExpr iivk = (InterfaceInvokeExpr) ivk;
			Type basetype = iivk.getBase().getType();
						
			if (basetype instanceof RefType) {
				RefType rt = (RefType)basetype;
				c = rt.getSootClass();
			} else {
				this.boogieStatements.addAll(createCallStatement(loc, ivk.getMethod(), throwsclauses, lefts, args));
				Log.error("Something wrong in findPossibleCalledMethods: "+ivk);
				return;
			}
		} else if (ivk instanceof SpecialInvokeExpr) {
			//special invoke is only used for constructor calls
			//TODO inheritance model
			//don't check if the base is defined for constructor calls
			//System.err.println("Special Call to : "+iivk.getMethod().getName());
		} else if (ivk instanceof VirtualInvokeExpr) {
			VirtualInvokeExpr iivk = (VirtualInvokeExpr) ivk;
			Type basetype = iivk.getBase().getType();
			
			if (basetype instanceof RefType) {
				RefType rt = (RefType)basetype;
				c = rt.getSootClass();
			} else {
				this.boogieStatements.addAll(createCallStatement(loc, ivk.getMethod(), throwsclauses, lefts, args));
				Log.error("Something wrong in findPossibleCalledMethods: "+ivk);
				return;
			}
		}
		
		if (c!=null) {
			//System.err.println("Call to "+c.getName()+" :: "+ivk);
			if (!findPossibleCalledMethods(loc, ivk.getMethod(), c, throwsclauses, lefts, args) ) {
				//if we cannot find any possible called method we just use the one in ivk.getMethod
				//which might be abstract
				//System.err.println("Warning: no suitable declared procedure for "+ ivk.getMethod().getName());
				this.boogieStatements.addAll(createCallStatement(loc, ivk.getMethod(), throwsclauses, lefts, args));
			} else {
				//System.err.println("Good! found stuff for "+ ivk.getMethod().getName());
			}
			//System.err.println("*******************");
		} else {
			//the procedure is static so we call it without checking if other
			//methods can be call depending on the type of c
			this.boogieStatements.addAll(createCallStatement(loc, ivk.getMethod(), throwsclauses, lefts, args));
		}
		
				

		
	}
	
	private void mergeThrowsClauses(List<SootClass> actualclause, List<SootClass> addedclause) {
		for (SootClass sc : addedclause) {
			//TODO: if actual clause contains sc or a supertype of sc, continue
			//else add sc to actual clause.
		}
	}
	
	
	private void translateInvokeAssignment(ILocation loc, Value lhs,
			InvokeExpr ivk, Unit statement) {

        // java.lang.String.length is treated as a special case:
        if (ivk.getMethod().getSignature()
                        .contains("<java.lang.String: int length()>")
                && lhs!=null) {
                if (ivk instanceof SpecialInvokeExpr) {
                        ((SpecialInvokeExpr) ivk).getBase().apply(this.valueswitch);                            
                } else if (ivk instanceof VirtualInvokeExpr) {
                        ((VirtualInvokeExpr) ivk).getBase().apply(this.valueswitch);                            
                } else {
                	throw new RuntimeException("Bad usage of String.length?");
                }
                Expression[] indices = { this.valueswitch.getExpression() };
                Expression right = this.pf.mkArrayAccessExpression(loc, this.pf.getIntType(), 
                		SootPrelude.v().getStringSizeHeapVariable(), indices); 

                lhs.apply(this.valueswitch);
                Expression left = this.valueswitch.getExpression();
                this.translateAssignment(loc, left, right);
                return;
        }

        if (ivk.getMethod().getSignature()
                        .contains("<java.lang.System: void exit(int)>")) {
                Log.info("Surppressing false positive from call to System.exit");

                //TODO: this is wrong for inter-procedural analysis!     
                enforcePostcondition();
                this.boogieStatements.add(this.pf.mkReturnStatement(loc));
                return;
        }
        		
		
		LinkedList<IdentifierExpression> lefts = new LinkedList<IdentifierExpression>();
		IdentifierExpression stubbedvar = null;
		Expression left = null;
		if (lhs != null) {
			lhs.apply(this.valueswitch);
			left = this.valueswitch.getExpression();
			if (left instanceof IdentifierExpression) {
				lefts.add((IdentifierExpression) left);
			} else {
				/**
				 * boogie doesn't allow you to put an array access as left hand
				 * side for a function call. So, if this happens, we add a fake
				 * local and assign it back after the call statement.
				 */
				stubbedvar = this.procInfo.createLocalVariable(left.getType());
				lefts.add(stubbedvar);
			}
		}

		//TODO: change that to the combination of the throw-clauses
		//of all possible calles
		List<SootClass> maxThrowSet = new LinkedList<SootClass>();

		
		SootMethod m = ivk.getMethod();
		
		int offset = (m.isStatic()) ? 0 : 1;
		Expression[] args = new Expression[ivk.getArgs().size() + offset];
		if (offset != 0) {
			args[0] = getCalleeInstance(ivk);
		}
		for (int i = 0; i < ivk.getArgs().size(); i++) {
			ivk.getArg(i).apply(this.valueswitch);
			args[i + offset] = this.valueswitch.getExpression();
		}
		/*
		 * In Boogie the left hand side of a call must have the same number of
		 * variables as the out params of the called procedure. If this is not
		 * the case, we inject dummy locals.
		 */
		
		findPossibleCalledMethods(loc, ivk, maxThrowSet, lefts, args);
				
		//now check if the procedure returned exceptional
		//and jump to the appropriate location 
		translateCalleeExceptions(loc, maxThrowSet, statement);
		
		/*
		 * if the left-hand side was an array access and we introduced a helper
		 * variable, we create and assignment here that assigns this variable to
		 * the original LHS.
		 */
		if (stubbedvar != null) {
			translateAssignment(loc, left, stubbedvar);
		}
	}

	/**
	 * (Only used after procedure calls) 
	 * Inserts a switch case to check if the exception variable has been set to
	 * any of the elements in maxThrowSet or any other exception that "statement"
	 * may throw according to the Unit Graph.
	 * If so, we add either add a jump to the appropriate handler or return. 
	 * @param loc
	 * @param maxThrowSet
	 * @param statement
	 */
	private void translateCalleeExceptions(ILocation loc, List<SootClass> maxThrowSet, Unit statement) {
		//keep track of the caught exceptions so that we can add the uncaught ones later
		HashSet<SootClass> caughtExceptions = new HashSet<SootClass>();
		//now collect all exceptions that are in the exceptional unit graph
		//and their associated traps
		if (this.procInfo.getExceptionalUnitGraph()
				.getExceptionalSuccsOf(statement).size() != 0) {
			for (ExceptionDest dest : this.procInfo.getExceptionalUnitGraph()
					.getExceptionDests(statement)) {
				if (dest.getTrap()!=null && dest.getTrap().getException()!=null) {
					caughtExceptions.add(dest.getTrap().getException());					
					Statement transitionStmt;					
					Expression condition = this.pf.mkBinaryExpression(loc, this.pf
							.getBoolType(), BinaryOperator.COMPNEQ, this.procInfo
							.getExceptionVariable(), SootPrelude.v()
							.getNullConstant());
						//the exception is caught somewhere in this procedure
						transitionStmt = this.pf.mkGotoStatement(
								loc,
								GlobalsCache.v().getUnitLabel(
										(Stmt) dest.getTrap().getHandlerUnit()));
						// add a conjunct to check if that the type of the exception
						// is <: than the one caught
						// by the catch block
						condition = this.pf.mkBinaryExpression(loc, this.pf
								.getBoolType(), BinaryOperator.LOGICAND, condition,
								this.pf.mkBinaryExpression(
										loc,
										this.pf.getBoolType(),
										BinaryOperator.COMPPO,
										this.valueswitch.getClassTypeFromExpression(
												this.procInfo
														.getExceptionVariable(),
												false),
										GlobalsCache.v().lookupClassVariable(
												dest.getTrap().getException())));				
						Statement[] thenPart = { transitionStmt };
						Statement[] elsePart = {};
						this.boogieStatements.add(this.pf.mkIfStatement(loc, condition,
								thenPart, elsePart));
				} else {					
					//Log.error("NO CATCH FOR "+ dest);
				}
			}
		}
		//now create a list of all exceptions that are thrown by the callee
		//but not caught by the procedure
		HashSet<SootClass> uncaughtException = new HashSet<SootClass>();
		for (SootClass sc : maxThrowSet) {
			boolean caught = false;
			for (SootClass other : caughtExceptions) {
				if (GlobalsCache.v().isSubTypeOrEqual(sc, other)) {
					caught = true; break;
				}
			}
			if (!caught && !uncaughtException.contains(sc)) {				
				uncaughtException.add(sc);
			}			
		}
		//now always pick the uncaught exception which has
		//no subclass in the hashset, create a conditional choice,
		//and remove it. This ordering is necessary, otherwise,
		//we might create dead code.
		LinkedList<SootClass> todo = new LinkedList<SootClass>(uncaughtException); 
		while(!todo.isEmpty()) {
			SootClass current = todo.removeFirst();
			boolean good = true;
			for (SootClass other : todo) {
				if (current==other) {
					throw new RuntimeException("can't be");
				}
				if (GlobalsCache.v().isSubTypeOrEqual(other, current)) {
					good = false; break;
				}
			}
			if (!good) {
				todo.addLast(current); continue;
			}
			Statement transitionStmt;					
			Expression condition = this.pf.mkBinaryExpression(loc, this.pf
					.getBoolType(), BinaryOperator.COMPNEQ, this.procInfo
					.getExceptionVariable(), SootPrelude.v()
					.getNullConstant());
			
			// add a conjunct to check if that the type of the exception
			// is <: than the one caught
			// by the catch block
			condition = this.pf.mkBinaryExpression(loc, this.pf
					.getBoolType(), BinaryOperator.LOGICAND, condition,
					this.pf.mkBinaryExpression(
							loc,
							this.pf.getBoolType(),
							BinaryOperator.COMPPO,
							this.valueswitch.getClassTypeFromExpression(
									this.procInfo
											.getExceptionVariable(),
									false),
							GlobalsCache.v().lookupClassVariable(
									current)));

			if (GlobalsCache.v().inThrowsClause(current, procInfo)) {
				enforcePostcondition();
				transitionStmt = this.pf.mkReturnStatement(loc);
			} else {
				//TODO: throw an error if this one is reachable!
				Log.error("TODO: deal with unexpected assertion: "+current);
				enforcePostcondition();
				transitionStmt = this.pf.mkReturnStatement(loc);
			}				
			Statement[] thenPart = { transitionStmt };
			Statement[] elsePart = {};
			this.boogieStatements.add(this.pf.mkIfStatement(loc, condition,
					thenPart, elsePart));						
		}
		
	}
	
	/**
	 * we use enforcePostcondition to ensure that a postcondition violation fires an exception.
	 * If we would do it correctly, we would enforce the postcondition only when we replace 
	 * function calls, but that wouldn't work with our current gradual verification.
	 */
	private void enforcePostcondition() {
		if (this.procInfo == null || this.procInfo.getBoogieProcedure()==null ||
				this.procInfo.getBoogieProcedure().getSpecification()==null) return;
        for (Specification spec : this.procInfo.getBoogieProcedure().getSpecification()) {
        	if (spec instanceof EnsuresSpecification) {
        		this.errorModel.createPostconditionViolationException(((EnsuresSpecification)spec).getFormula().clone());	
        	}
        }		
	}
	
	private Expression getCalleeInstance(InvokeExpr ivk) {
//		if (ivk instanceof InstanceInvokeExpr) {			
//			InstanceInvokeExpr iivk = (InstanceInvokeExpr) ivk;
//			iivk.getBase().apply(this.valueswitch);
//			Expression base = this.valueswitch.getExpression();
//			
//			return base;
//		} else 
		if (ivk instanceof InterfaceInvokeExpr) {
			//TODO: error model
			//inheritance model
			InterfaceInvokeExpr iivk = (InterfaceInvokeExpr) ivk;
			iivk.getBase().apply(this.valueswitch);
			Expression base = this.valueswitch.getExpression();
			this.errorModel.createNonNullGuard(base);
			return base;
		} else if (ivk instanceof SpecialInvokeExpr) {
			//special invoke is only used for constructor calls
			//TODO inheritance model
			//don't check if the base is defined for constructor calls
			SpecialInvokeExpr iivk = (SpecialInvokeExpr) ivk;
			iivk.getBase().apply(this.valueswitch);
			return this.valueswitch.getExpression();
		} else if (ivk instanceof VirtualInvokeExpr) {
			VirtualInvokeExpr iivk = (VirtualInvokeExpr) ivk;
			iivk.getBase().apply(this.valueswitch);
			Expression base = this.valueswitch.getExpression();
			//TODO: maybe we want to check that base != this ... 
			//but this is hard in Jimple
			this.errorModel.createNonNullGuard(base);			
			return base;
		}
		throw new RuntimeException(
				"Cannot compute instance for static or dynamic call");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * soot.jimple.StmtSwitch#caseBreakpointStmt(soot.jimple.BreakpointStmt)
	 */
	@Override
	public void caseBreakpointStmt(BreakpointStmt arg0) {
		injectLabelStatements(arg0);
		Log.info("Joogie does not translate BreakpointStmt");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * soot.jimple.StmtSwitch#caseEnterMonitorStmt(soot.jimple.EnterMonitorStmt)
	 * If this is only for synchronization, we don't need to translate it
	 */
	@Override
	public void caseEnterMonitorStmt(EnterMonitorStmt arg0) {
		injectLabelStatements(arg0);
		Log.info("Joogie does not translate EnterMonitor");		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * soot.jimple.StmtSwitch#caseExitMonitorStmt(soot.jimple.ExitMonitorStmt)
	 * If this is only for synchronization, we don't need to translate it
	 */
	@Override
	public void caseExitMonitorStmt(ExitMonitorStmt arg0) {
		injectLabelStatements(arg0);
		Log.info("Joogie does not translate ExitMonitor");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see soot.jimple.StmtSwitch#caseGotoStmt(soot.jimple.GotoStmt)
	 */
	@Override
	public void caseGotoStmt(GotoStmt arg0) {
		injectLabelStatements(arg0);
		ILocation loc = TranslationHelpers.translateLocation(arg0.getTags());
		String labelName = GlobalsCache.v().getUnitLabel(
				(Stmt) arg0.getTarget());
		this.boogieStatements.add(this.pf.mkGotoStatement(loc, labelName));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see soot.jimple.StmtSwitch#caseIdentityStmt(soot.jimple.IdentityStmt)
	 */
	@Override
	public void caseIdentityStmt(IdentityStmt arg0) {
		injectLabelStatements(arg0);
		ILocation loc = TranslationHelpers.translateLocation(arg0.getTags());
		translateAssignment(loc, arg0.getLeftOp(), arg0.getRightOp(), arg0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see soot.jimple.StmtSwitch#caseIfStmt(soot.jimple.IfStmt)
	 */
	@Override
	public void caseIfStmt(IfStmt arg0) {
		injectLabelStatements(arg0);
		ILocation loc = TranslationHelpers.translateLocation(arg0.getTags());
		Statement[] thenPart = { this.pf.mkGotoStatement(loc, GlobalsCache.v()
				.getUnitLabel(arg0.getTarget())) };
		Statement[] elsePart = {};
		arg0.getCondition().apply(this.valueswitch);
		Expression cond = TranslationHelpers.castBoogieTypes(
				this.valueswitch.getExpression(), this.pf.getBoolType());
		this.boogieStatements.add(this.pf.mkIfStatement(loc, cond, thenPart,
				elsePart));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see soot.jimple.StmtSwitch#caseInvokeStmt(soot.jimple.InvokeStmt)
	 */
	@Override
	public void caseInvokeStmt(InvokeStmt arg0) {
		injectLabelStatements(arg0);
		ILocation loc = TranslationHelpers.translateLocation(arg0.getTags());
		translateAssignment(loc, null, arg0.getInvokeExpr(), arg0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * soot.jimple.StmtSwitch#caseLookupSwitchStmt(soot.jimple.LookupSwitchStmt)
	 */
	@Override
	public void caseLookupSwitchStmt(LookupSwitchStmt arg0) {
		injectLabelStatements(arg0);
		LinkedList<Expression> cases = new LinkedList<Expression>();
		LinkedList<Statement[]> targets = new LinkedList<Statement[]>();

		arg0.getKey().apply(this.valueswitch);
		Expression key = this.valueswitch.getExpression();
		for (int i = 0; i < arg0.getTargetCount(); i++) {
			ILocation loc = TranslationHelpers.translateLocation(arg0
					.getTarget(i).getTags());
			Expression cond = this.pf.mkBinaryExpression(
					loc,
					this.pf.getBoolType(),
					BinaryOperator.COMPEQ,
					key,
					this.pf.mkIntLiteral(loc,
							Integer.toString(arg0.getLookupValue(i))));
			cases.add(cond);
			Statement[] gototarget = { this.pf.mkGotoStatement(loc,
					GlobalsCache.v().getUnitLabel((Stmt) arg0.getTarget(i))) };
			targets.add(gototarget);
		}
		{
			ILocation loc = TranslationHelpers.translateLocation(arg0
					.getDefaultTarget().getTags());
			Statement[] gototarget = { this.pf.mkGotoStatement(
					loc,
					GlobalsCache.v().getUnitLabel(
							(Stmt) arg0.getDefaultTarget())) };
			targets.add(gototarget);
		}
		translateSwitch(cases, targets);
	}

	/**
	 * note that there is one more target than cases because of the default
	 * cases
	 * 
	 * @param cases
	 * @param targets
	 */
	private void translateSwitch(LinkedList<Expression> cases,
			LinkedList<Statement[]> targets) {
		Statement[] elseblock = targets.getLast();
		Statement ifstatement = null;
		int max = cases.size() - 1;
		for (int i = max; i >= 0; i--) {
			Statement[] thenblock = targets.get(i);
			ifstatement = this.pf.mkIfStatement(cases.get(i).getLocation(),
					cases.get(i), thenblock, elseblock);
			elseblock = new Statement[1];
			elseblock[0] = ifstatement;
		}
		if (ifstatement != null) {
			this.boogieStatements.add(ifstatement);
		} else {
			Log.info("Warning: Found empty switch statement (or only default case).");			 
			for (int i=0; i<elseblock.length; i++) {
				this.boogieStatements.add(elseblock[i]);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see soot.jimple.StmtSwitch#caseNopStmt(soot.jimple.NopStmt)
	 */
	@Override
	public void caseNopStmt(NopStmt arg0) {
		injectLabelStatements(arg0);
		//Log.error("NopStmt: " + arg0.toString());
		//assert (false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see soot.jimple.StmtSwitch#caseRetStmt(soot.jimple.RetStmt)
	 */
	@Override
	public void caseRetStmt(RetStmt arg0) {
		injectLabelStatements(arg0);
		Log.error("This is deprecated: " + arg0.toString());
		throw new RuntimeException("caseRetStmt is not implemented. Contact developers!");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see soot.jimple.StmtSwitch#caseReturnStmt(soot.jimple.ReturnStmt)
	 */
	@Override
	public void caseReturnStmt(ReturnStmt arg0) {
		injectLabelStatements(arg0);
		ILocation loc = TranslationHelpers.translateLocation(arg0.getTags());
		if (this.procInfo.getReturnVariable() != null) {
			Expression lhs = this.procInfo.getReturnVariable();
			arg0.getOp().apply(this.valueswitch);
			Expression rhs = this.valueswitch.getExpression();
			translateAssignment(loc, lhs, rhs);
		}
		enforcePostcondition();
		this.boogieStatements.add(this.pf.mkReturnStatement(loc));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * soot.jimple.StmtSwitch#caseReturnVoidStmt(soot.jimple.ReturnVoidStmt)
	 */
	@Override
	public void caseReturnVoidStmt(ReturnVoidStmt arg0) {
		injectLabelStatements(arg0);
		ILocation loc = TranslationHelpers.translateLocation(arg0.getTags());
		enforcePostcondition();
		this.boogieStatements.add(this.pf.mkReturnStatement(loc));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * soot.jimple.StmtSwitch#caseTableSwitchStmt(soot.jimple.TableSwitchStmt)
	 * The TableSwitch is a special case of the LookupSwitch, where all cases
	 * are consecutive.
	 */
	@Override
	public void caseTableSwitchStmt(TableSwitchStmt arg0) {
		injectLabelStatements(arg0);
		LinkedList<Expression> cases = new LinkedList<Expression>();
		LinkedList<Statement[]> targets = new LinkedList<Statement[]>();

		arg0.getKey().apply(this.valueswitch);
		Expression key = this.valueswitch.getExpression();
		int counter=0;
		for (int i = arg0.getLowIndex(); i <= arg0.getHighIndex(); i++) {
			ILocation loc = TranslationHelpers.translateLocation(arg0
					.getTarget(counter).getTags());
			Expression cond = this.pf.mkBinaryExpression(loc,
					this.pf.getBoolType(), BinaryOperator.COMPEQ, key,
					this.pf.mkIntLiteral(loc, Integer.toString(i)));
			cases.add(cond);
			Statement[] gototarget = { this.pf.mkGotoStatement(loc,
					GlobalsCache.v().getUnitLabel((Stmt) arg0.getTarget(counter))) };
			targets.add(gototarget);
			counter++;
		}
		{
			ILocation loc = TranslationHelpers.translateLocation(arg0
					.getDefaultTarget().getTags());
			Statement[] gototarget = { this.pf.mkGotoStatement(
					loc,
					GlobalsCache.v().getUnitLabel(
							(Stmt) arg0.getDefaultTarget())) };
			targets.add(gototarget);
		}
		translateSwitch(cases, targets);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see soot.jimple.StmtSwitch#caseThrowStmt(soot.jimple.ThrowStmt)
	 */
	@Override
	public void caseThrowStmt(ThrowStmt arg0) {
		injectLabelStatements(arg0);
		ILocation loc = TranslationHelpers.translateLocation(arg0.getTags());
		arg0.getOp().apply(this.valueswitch);
		Expression right = this.valueswitch.getExpression();
		// assign the value from arg0.getOp() to the $exception variable of
		// the current procedure.
		// Note that this only works because soot moves the "new" statement
		// to a new local variable.
		this.translateAssignment(loc, this.procInfo.getExceptionVariable(),
				right);		
		// Add a goto statement to the exceptional successors.
		List<Unit> exc_succ = procInfo.getExceptionalUnitGraph()
				.getExceptionalSuccsOf((Unit) arg0);
		String[] labels = new String[exc_succ.size()]; 
		if (exc_succ.size() > 0) {
			for (int i = 0; i < exc_succ.size(); i++) {
				labels[i] = GlobalsCache.v().getUnitLabel(
						(Stmt) exc_succ.get(i));
			}
			if (exc_succ.size()> 1) {
//				StringBuilder sb = new StringBuilder();
//				sb.append("Throw statement may jump to more than one location: "+this.procInfo.getBoogieName() + ":"+this.currentStatement+"\n");
//				sb.append("Line "+loc.getStartLine()+"\n");
//				sb.append(arg0.getOp()+"\n");
				

				for (int i = 0; i < exc_succ.size(); i++) {
					Unit u = exc_succ.get(i);
					//sb.append("Line "+Util.findLineNumber(u.getTags())+ ": "+u+"\n");

					if (u instanceof IdentityStmt) {
						//sb.append("IdentityStmt ");
						IdentityStmt istmt = (IdentityStmt)u;
						if (istmt.getRightOp() instanceof CaughtExceptionRef) {														
							//sb.append("... catches exception! " + istmt.getLeftOp().getType()+"\n");							
							Type caughttype = istmt.getLeftOp().getType();							
							if (!(caughttype instanceof RefType)) {
								throw new RuntimeException("Bug in translation of ThrowStmt!");
							}							
							RefType caught = (RefType)caughttype;
							Expression cond = GlobalsCache.v().compareTypeExpressions(
									this.valueswitch.getClassTypeFromExpression(right, false), 
									GlobalsCache.v().lookupClassVariable(caught.getSootClass()));
							Statement[] thenPart = new Statement[] { this.pf.mkGotoStatement(loc, labels[i]) };
							Statement ifstmt = this.pf.mkIfStatement(loc, cond, thenPart, new Statement[0]);
							//sb.append("created choice: "+ifstmt+"\n");
							this.boogieStatements.add(ifstmt);							
						} else {
							throw new RuntimeException("Bug in translation of ThrowStmt!");
						}
					} else {
						throw new RuntimeException("Bug in translation of ThrowStmt!");
					}
				}
				//throw new RuntimeException(sb.toString());
				//Log.error(sb);
				//Make sure that the execution does not continue after the throw statement
				this.boogieStatements.add(this.pf.mkReturnStatement(loc));				
			} else {
				this.boogieStatements.add(this.pf.mkGotoStatement(loc, labels[0]));
			}
		} else {
			enforcePostcondition();
			this.boogieStatements.add(this.pf.mkReturnStatement(loc));
		}

	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see soot.jimple.StmtSwitch#defaultCase(java.lang.Object)
	 */
	@Override
	public void defaultCase(Object arg0) {
		assert (false);
	}

}