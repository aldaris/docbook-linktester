import java.io.File

def output = new File(basedir, "linkTester.err").text
assert output.contains("no errors reported")
