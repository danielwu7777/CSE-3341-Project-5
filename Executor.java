import java.util.*;

class CoreVar {
	Core type;
	Integer value;

	public CoreVar(Core varType) {
		type = varType;
		if (type == Core.INT) {
			value = 0;
		} else {
			value = null;
		}
	}
}

class Executor {

	static HashMap<String, CoreVar> globalSpace;
	static ArrayList<Integer> heapSpace;
	static ArrayList<Integer> refCounts;

	static Scanner dataFile;

	// stackSpace is now our call stack
	static Stack<Stack<HashMap<String, CoreVar>>> stackSpace;

	// This will store all FuncDecls so we can look up the function being called
	static HashMap<String, FuncDecl> funcDefinitions;

	/*
	 * Overriding some methods from the super class to handle the call stack
	 */

	static void initialize(String dataFileName) {
		globalSpace = new HashMap<String, CoreVar>();
		heapSpace = new ArrayList<Integer>();
		refCounts = new ArrayList<Integer>();
		dataFile = new Scanner(dataFileName);

		stackSpace = new Stack<Stack<HashMap<String, CoreVar>>>();
		funcDefinitions = new HashMap<String, FuncDecl>();
	}

	static void pushLocalScope() {
		stackSpace.peek().push(new HashMap<String, CoreVar>());
	}

	static void popLocalScope() {
		List<String> refVarsToCollect = new ArrayList<>();
		HashMap<String, CoreVar> map = new HashMap<>();
		map.putAll(stackSpace.peek().peek());
		for (String identifier : map.keySet()) {
			CoreVar x = map.get(identifier);
			if (x.type.equals(Core.REF) && x.value != null) {
				refVarsToCollect.add(identifier);
			}
		}
		decrRefCounts(refVarsToCollect);
		stackSpace.peek().pop();
	}

	static int getNextData() {
		int data = 0;
		if (dataFile.currentToken() == Core.EOS) {
			System.out.println("ERROR: data file is out of values!");
			System.exit(0);
		} else {
			data = dataFile.getCONST();
			dataFile.nextToken();
		}
		return data;
	}

	static void allocate(String identifier, Core varType) {
		CoreVar record = new CoreVar(varType);
		// If we are in the DeclSeq, no frames will have been created yet
		if (stackSpace.size() == 0) {
			globalSpace.put(identifier, record);
		} else {
			stackSpace.peek().peek().put(identifier, record);
		}
	}

	static CoreVar getStackOrStatic(String identifier) {
		CoreVar record = null;
		for (int i = stackSpace.peek().size() - 1; i >= 0; i--) {
			if (stackSpace.peek().get(i).containsKey(identifier)) {
				record = stackSpace.peek().get(i).get(identifier);
				break;
			}
		}
		if (record == null) {
			record = globalSpace.get(identifier);
		}
		return record;
	}

	static void heapAllocate(String identifier) {
		CoreVar x = getStackOrStatic(identifier);
		if (x.type != Core.REF) {
			System.out.println("ERROR: " + identifier + " is not of type ref, cannot perform \"new\"-assign!");
			System.exit(0);
		}
		x.value = heapSpace.size();
		heapSpace.add(null);
	}

	static Core getType(String identifier) {
		CoreVar x = getStackOrStatic(identifier);
		return x.type;
	}

	static Integer getValue(String identifier) {
		CoreVar x = getStackOrStatic(identifier);
		Integer value = x.value;
		if (x.type == Core.REF) {
			try {
				value = heapSpace.get(value);
			} catch (Exception e) {
				System.out.println("ERROR: invalid heap read attempted!");
				System.exit(0);
			}
		}
		return value;
	}

	static void storeValue(String identifier, int value) {
		CoreVar x = getStackOrStatic(identifier);
		if (x.type == Core.REF) {
			try {
				heapSpace.set(x.value, value);
			} catch (Exception e) {
				System.out.println("ERROR: invalid heap write attempted!");
				System.exit(0);
			}
		} else {
			x.value = value;
		}
	}

	static void referenceCopy(String var1, String var2) {
		CoreVar x = getStackOrStatic(var1);
		CoreVar y = getStackOrStatic(var2);
		if (y.value != null) {
			addRefCount(y.value);
		}
		if (x.value != null) {
			subtractRefCountAtPos(x.value);
		}
		x.value = y.value;
	}

	/*
	 * New methods to handle pushing/popping frames and storing function definitions
	 */

	static void storeFuncDef(Id name, FuncDecl definition) {
		funcDefinitions.put(name.getString(), definition);
	}

	static Formals getFormalParams(Id name) {
		if (!funcDefinitions.containsKey(name.getString())) {
			System.out.println("ERROR: Function call " + name.getString() + " has no target!");
			System.exit(0);
		}
		return funcDefinitions.get(name.getString()).getFormalParams();
	}

	static StmtSeq getBody(Id name) {
		return funcDefinitions.get(name.getString()).getBody();
	}

	static void pushFrame() {
		stackSpace.push(new Stack<HashMap<String, CoreVar>>());
		pushLocalScope();
	}

	static List<String> pushFrame(Formals formalParams, Formals actualParams) {
		List<String> formals = formalParams.execute();
		List<String> actuals = actualParams.execute();

		Stack<HashMap<String, CoreVar>> newFrame = new Stack<HashMap<String, CoreVar>>();
		newFrame.push(new HashMap<String, CoreVar>());

		for (int i = 0; i < formals.size(); i++) {
			CoreVar temp = new CoreVar(Core.REF);
			temp.value = getStackOrStatic(actuals.get(i)).value;
			if (actuals.get(i) != null) {
				addRefCount(i);
			}
			// System.out.println(formals.get(i) + " " + actuals.get(i) + " passing:" +
			// temp.value+ " heap:" + heapSpace.get(temp.value));
			newFrame.peek().put(formals.get(i), temp);
		}
		stackSpace.push(newFrame);
		pushLocalScope();
		return actuals;
	}

	static void popFrame() {
		List<String> refVarsToCollect = new ArrayList<>();
		// Make a copy of the top of stackSpace
		Stack<HashMap<String, CoreVar>> frame = new Stack<>();
		frame.addAll(stackSpace.peek());
		HashMap<String, CoreVar> map;
		while (frame.size() > 0) {
			map = frame.pop();
			for (String identifier : map.keySet()) {
				CoreVar x = map.get(identifier);
				if (x.type.equals(Core.REF) && x.value != null) {
					refVarsToCollect.add(identifier);
				}
			}
		}
		decrRefCounts(refVarsToCollect);
		stackSpace.pop();
	}

	// New methods for garbage collector

	static void refCountsAllocate() {
		refCounts.add(1);
		printSumRefCounts();
	}

	static void addRefCount(int position) {
		refCounts.set(position, refCounts.get(position) + 1);
		if (refCounts.get(position) == 1) {
			printSumRefCounts();
		}
	}

	static void decrRefCounts(List<String> refVars) {
		for (String identifier : refVars) {
			CoreVar x = getStackOrStatic(identifier);
			subtractRefCountAtPos(x.value);
		}
	}

	static void subtractRefCountAtPos(int position) {
		refCounts.set(position, refCounts.get(position) - 1);
		if (refCounts.get(position) == 0) {
			printSumRefCounts();
		}
	}

	static void printSumRefCounts() {
		int numReachable = 0;
		for (int i : refCounts) {
			if (i > 0) {
				numReachable++;
			}
		}
		System.out.println("gc:" + numReachable);
	}

	static void clearReferences() {
		for (int i = 0; i < refCounts.size(); i++) {
			if (refCounts.get(i) > 0) {
				refCounts.set(i, 0);
				printSumRefCounts();
			}
		}
	}
}