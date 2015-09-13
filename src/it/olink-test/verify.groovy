import java.io.File

def output = new File(basedir, "linkTester.err").text
assert output.contains("no-such-book#missing-chapter")
assert output.contains("#incorrect-link")
assert output.contains("book1#invalid-cross-book-link")
assert output.contains("book2#wrong-book-link")
assert output.contains("book2#broken-olink")
assert !output.contains("first-chapter")
assert !output.contains("book1#chap-external")
