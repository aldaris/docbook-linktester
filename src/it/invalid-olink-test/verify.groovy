import java.io.File

def output = new File(basedir, "linkTester.err").text
assert output.contains("WARNING")
assert output.contains("Olink not in targetdoc#targetptr format")
assert output.contains("http://foobar.com")
