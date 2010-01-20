try {

def jnlp = new File(basedir, 'target/jws/launch.jnlp')
assert jnlp.exists()
jnlp.eachLine { line -> assert line.indexOf('${') < 0 : "non evaluated value in :" + line }

def html = new File(basedir, 'target/jws/run.html')
assert html.exists()
html.eachLine { line -> assert line.indexOf('${') < 0 : "non evaluated value in :" + line }

return true
} catch(Throwable e) {
  e.printStackTrace()
  return false
}