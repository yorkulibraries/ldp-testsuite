package org.w3.ldp.testsuite.reporter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.testng.annotations.Test;
import org.w3.ldp.testsuite.annotations.SpecTest;
import org.w3.ldp.testsuite.test.BasicContainerTest;
import org.w3.ldp.testsuite.test.DirectContainerTest;
import org.w3.ldp.testsuite.test.IndirectContainerTest;
import org.w3.ldp.testsuite.test.MemberResourceTest;
import org.w3.ldp.testsuite.test.NonRDFSourceTest;
import org.w3.ldp.testsuite.vocab.LDP;
import org.w3.ldp.testsuite.vocab.TestDescription;

import com.github.jsonldjava.jena.JenaJSONLD;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFList;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.sparql.vocabulary.EARL;
import com.hp.hpl.jena.vocabulary.DC;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import com.hp.hpl.jena.vocabulary.TestManifest;

public class LdpEarlTestManifest {

	private static BufferedWriter writer;
	private static Model model;

	private static Property declaredInClass, declaredTestCase, conformanceLevel;


	private static Class<BasicContainerTest> bcTest = BasicContainerTest.class;
	private static Class<IndirectContainerTest> indirectContainerTest = IndirectContainerTest.class;
	private static Class<DirectContainerTest> directContianerTest = DirectContainerTest.class;
	private static Class<MemberResourceTest> memberResourceTest = MemberResourceTest.class;
	private static Class<NonRDFSourceTest> nonRdfSourceTest = NonRDFSourceTest.class;

	private static final String TURTLE = "TURTLE";

	private static final String outputDir = "report"; // directory where results

	static {
		JenaJSONLD.init();
	}

	public static void main(String[] args) throws IOException {
		try {
			createWriter(outputDir);
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}

		model = ModelFactory.createDefaultModel();
		declaredInClass = ResourceFactory
				.createProperty(LDP.LDPT_NAMESPACE + "declaredInClass");
		declaredTestCase = ResourceFactory
				.createProperty(LDP.LDPT_NAMESPACE + "declaredTestCase");
		conformanceLevel = ResourceFactory
				.createProperty(LDP.LDPT_NAMESPACE + "conformanceLevel");

		writePrefixes(model);
		// Use ArrayList to preserve order
		ArrayList<Resource> testcases = new ArrayList<Resource>();
		writeTestClasses(testcases);

		write();
		try {
			endWriter();
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	private static void writeManifest(ArrayList<Resource> testcases, String localName, String label, String description) {
		if (testcases.size() == 0) return;
		
		Resource manifest = model.createResource(LDP.LDPT_NAMESPACE + localName+"Manifest",
				TestManifest.Manifest);
		manifest.addProperty(DC.title, label);
		manifest.addProperty(TestManifest.name, label);
		manifest.addProperty(RDFS.comment, description);
		Resource[] ra={};
		RDFList l = model.createList(testcases.toArray(ra));
		manifest.addProperty(TestManifest.entries, l);
	}

	public static void writePrefixes(Model model) {
		model.setNsPrefix("doap", "http://usefulinc.com/ns/doap#");
		model.setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/");
		model.setNsPrefix("earl", "http://www.w3.org/ns/earl#");
		model.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
		model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
		model.setNsPrefix("mf",
				"http://www.w3.org/2001/sw/DataAccess/tests/test-manifest#");
		model.setNsPrefix("rdft", "http://www.w3.org/ns/rdftest#");
		model.setNsPrefix("dcterms", "http://purl.org/dc/terms/");
		model.setNsPrefix("td", "http://www.w3.org/2006/03/test-description#");
		model.setNsPrefix(LDP.LDPT_PREFIX, LDP.LDPT_NAMESPACE);
	}

	private static <T> void writeInfo(Class<T> testClass, String title, String description) {
		ArrayList<ArrayList<Resource>> conformanceClasses = new ArrayList<ArrayList<Resource>>();
        conformanceClasses.add(new ArrayList<Resource>());
        conformanceClasses.add(new ArrayList<Resource>());
        conformanceClasses.add(new ArrayList<Resource>());
        conformanceClasses.add(new ArrayList<Resource>());
        
		String className = testClass.getCanonicalName();
		Method[] methods = testClass.getMethods();
		for (Method method : methods) {
			if (method.isAnnotationPresent(Test.class)) {
				generateInformation(method, className, conformanceClasses);
			}
		}
		writeManifest(conformanceClasses.get(LdpTestCaseReporter.MUST), title
				+ "-MUST", title + " (MUST)", description
				+ " MUST conformance tests.");
		writeManifest(conformanceClasses.get(LdpTestCaseReporter.SHOULD), title
				+ "-SHOULD", title + " (SHOULD)", description
				+ " SHOULD conformance tests.");
		writeManifest(conformanceClasses.get(LdpTestCaseReporter.MAY), title
				+ "-MAY", title + " (MAY)", description
				+ " MAY conformance tests.");
		writeManifest(conformanceClasses.get(LdpTestCaseReporter.OTHER), title
				+ "-OTHER", title + " (OTHER)", description
				+ " No official conformance status.");
	}

	private static Resource generateInformation(Method method, String className, 
			ArrayList<ArrayList<Resource>> conformanceClasses) {
		SpecTest testLdp = null;
		Test test = null;
		if (method.getAnnotation(SpecTest.class) != null
				&& method.getAnnotation(Test.class) != null) {
			testLdp = method.getAnnotation(SpecTest.class);
			test = method.getAnnotation(Test.class);
			String allGroups = groups(test.groups());

			Calendar cal = GregorianCalendar.getInstance();
			Literal date = model.createTypedLiteral(cal);

			String testCaseName = createTestCaseName(className, method.getName());
			String testCaseDeclaringName = createTestCaseName(method.getDeclaringClass().getCanonicalName(), method.getName());
			String testCaseURL = LDP.LDPT_NAMESPACE + testCaseName;
			String testCaseDeclaringURL = LDP.LDPT_NAMESPACE + testCaseDeclaringName;

			Resource testCaseResource = model.createResource(testCaseURL);
			testCaseResource.addProperty(RDF.type, EARL.TestCase);
			testCaseResource.addProperty(RDFS.label, testCaseName);
			testCaseResource.addProperty(TestManifest.name, testCaseName);
			testCaseResource.addProperty(DCTerms.date, date);

			testCaseResource.addProperty(RDFS.comment, test.description());
			if (allGroups != null)
				testCaseResource.addProperty(DCTerms.subject, allGroups);
			else
				conformanceClasses.get(LdpTestCaseReporter.OTHER).add(testCaseResource);

			for (String group: test.groups()) {
				group = group.trim();
				testCaseResource.addProperty(conformanceLevel, model.createResource(LDP.LDPT_NAMESPACE + group));
				conformanceClasses.get(LdpTestCaseReporter.getConformanceIndex(group)).add(testCaseResource);
			}
			
			// Leave action property only to make earl-report happy
			testCaseResource.addProperty(TestManifest.action, "");
			
			switch (testLdp.approval()) {
			case WG_APPROVED:
				testCaseResource.addProperty(TestDescription.reviewStatus, TestDescription.approved);
				break;
			case WG_PENDING:
				testCaseResource.addProperty(TestDescription.reviewStatus, TestDescription.unreviewed);
				break;
			default:
				testCaseResource.addProperty(TestDescription.reviewStatus, TestDescription.unreviewed);
				break;
			}

			testCaseResource.addProperty(declaredInClass, className);
			testCaseResource.addProperty(declaredTestCase, model.createResource(testCaseDeclaringURL));
			Resource specRef = null;
			if (testLdp.specRefUri() != null) {
				specRef = model.createResource(testLdp.specRefUri());
				testCaseResource.addProperty(RDFS.seeAlso, specRef);
			}
			
			if (test.description() != null && test.description().length() > 0) {
				Resource excerpt = model.createResource(TestDescription.Excerpt);
				excerpt.addLiteral(TestDescription.includesText, test.description());
				if (specRef != null) {
					excerpt.addProperty(RDFS.seeAlso, specRef);
				}
				testCaseResource.addProperty(TestDescription.specificationReference, excerpt);
			}
			
			return testCaseResource;
		}
		return null;
	}

	public static String createTestCaseURL(String className, String methodName) {
		return LDP.LDPT_NAMESPACE + createTestCaseName(className, methodName);
	}
	
	public static String createTestCaseName(String className, String methodName) {

		className = className.substring(className.lastIndexOf(".") + 1);

		if (className.endsWith("Test")) {
			className = className.substring(0, className.length()-4);
		}
		if (methodName.startsWith("test")) {
			methodName = methodName.substring(4, methodName.length());
		}
		return className + "-" + methodName;
	}

	private static String groups(String[] list) {
		if (list.length == 0)
			return null;
		String retList = "";
		for (int i = 0; i < list.length; i++) {
			if (i == list.length - 1)
				retList += list[i];
			else
				retList += list[i] + ", ";
		}
		return retList;
	}

	private static void writeTestClasses(ArrayList<Resource> testcases) {
		// These are put in order so they are presented properly on earl-report
//		writeInfo(commonResourceTest, testcases);
//		writeInfo(rdfSourceTest, testcases);
//		writeInfo(commonContainerTest, testcases);
		writeInfo(memberResourceTest, "RDFSource", "LDP RDF Source tests.");
		writeInfo(nonRdfSourceTest, "Non-RDFSource", "LDP Non-RDF Source tests.");
		writeInfo(bcTest, "BasicContainer", "LDP Basic Container tests.");
		writeInfo(directContianerTest, "DirectContainer", "LDP Direct Container tests.");
		writeInfo(indirectContainerTest, "IndirectContainer", "LDP Indirect Container tests.");
	}

	private static void write() {
		model.write(writer, TURTLE);
	}

	private static void createWriter(String directory) throws IOException {
		writer = null;
		new File(directory).mkdirs();
		writer = new BufferedWriter(new FileWriter(directory
				+ "/ldp-earl-manifest.ttl"));
	}

	private static void endWriter() throws IOException {
		writer.flush();
		writer.close();
	}
}