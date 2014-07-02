

/**
 * @author schaef
 *
 */
public class InfeasibleCode01 {
//	public InfeasibleCode01 bases;
//	// from org.openecard.bouncycastle.crypto.params.NTRUSigningPrivateKeyParameters	
//	boolean foo(InfeasibleCode01 other) {
//		if (bases == null) {
//			if (other.bases != null) {
//				return false;
//			}
//		}
//		if (bases.hashCode() != other.bases.hashCode()) {
//			return false;
//		}
//		
//		if ("hallo" == "wurstsalat") return false;
//		
//		// ...
//		return true;
//	}
	

	public int infeasible0(int[] arr) {
		int i = arr.length;
		arr[3]=3;
		return arr[i]; // INFEASIBLE
	}

	public int infeasible1(Object o) {
		if (o!=null) {
			return o.hashCode(); // INFEASIBLE
		} 
		System.err.println(o.toString() + " does not exist");
		return 2;
	}

	public void infeasible2(int [] arr) {
		for (int i=0; i<=arr.length;i++) {
			arr[i]=i; // INFEASIBLE
		}
	}
	
	public void infeasible3(int a, int b) {
		b=1; // ALL INFEASIBLE
		if (a>0) b--;
		b=1/b;
		if (a<=0) b=1/(1-b);
	}
	
	public boolean infeasible4(Object o) {
		System.err.println(o.toString());
		if (o==null) {
			return false; // INFEASIBLE
		}
		return true;
	}
	
	public void infeasible5() {
		String test="too long";
		if (test.length()==3) {
			System.err.println("unreachable"); // INFEASIBLE
		}
	}
	
	public int infeasible6(int[] arr) {
		return arr[-1] + arr[arr.length]; // INFEASIBLE
	}	
	
}


