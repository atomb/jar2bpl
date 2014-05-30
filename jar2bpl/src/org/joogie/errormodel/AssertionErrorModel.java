/**
 * 
 */
package org.joogie.errormodel;

import org.joogie.soot.SootProcedureInfo;
import org.joogie.soot.SootStmtSwitch;
import org.joogie.util.TranslationHelpers;

import soot.SootClass;
import boogie.expression.Expression;
import boogie.location.ILocation;
import boogie.statement.Statement;

/**
 * @author martin
 *
 */
public class AssertionErrorModel extends AbstractErrorModel {

	/**
	 * @param pinfo
	 * @param stmtswitch
	 */
	public AssertionErrorModel(SootProcedureInfo pinfo,
			SootStmtSwitch stmtswitch) {
		super(pinfo, stmtswitch);
	}
	
	
	public void createdExpectedException(Expression guard, SootClass exception) {
		//TODO:
		createdUnExpectedException(guard, exception);
	}
	
	public void createdUnExpectedException(Expression guard, SootClass exception) {
		ILocation loc = TranslationHelpers.translateLocation(this.stmtSwitch.getCurrentStatement().getTags());
		Statement assertion;
		if (guard!=null) {
			//assertion = this.pf.mkAssertStatement(loc,this.pf.mkUnaryExpression(loc, guard.getType(), UnaryOperator.LOGICNEG, guard));
			assertion = this.pf.mkAssertStatement(loc,guard);
		} else {
			//assertion = this.pf.mkAssertStatement(loc,this.pf.mkBooleanLiteral(loc, false));
			//TODO:
			assertion = this.pf.mkReturnStatement(loc);
		}		
		this.stmtSwitch.addGuardStatement(assertion);		
	}
	
	

}