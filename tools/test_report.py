import glob,re,html,sys
sys.stdout.reconfigure(encoding='utf-8')
for p in glob.glob('core/build/test-results/test/*.xml'):
    s=open(p,encoding='utf-8').read()
    m=re.search(r'tests="(\d+)".*?failures="(\d+)"',s); print(m.groups())
    for fm in re.findall(r'<testcase name="([^"]+)"[^>]*>\s*<failure message="([^"]{0,300})',s): print(fm[0], html.unescape(fm[1])[:300])
    so=re.search(r'<system-out><!\[CDATA\[(.*?)\]\]>',s,re.S)
    if so: print(so.group(1))
