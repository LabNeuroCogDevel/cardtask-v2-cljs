.PHONY: all run-clj run-bin run-jar debug-figwheel
all: psiclj/psiclj

out/main.js: src/cardtask/*.cljs
	clj -M --main cljs.main --optimizations advanced -c cardtask.core

psiclj/psiclj: psiclj/src/psiclj.clj out/main.js
	# sudo archlinux-java set java-11-graalvm
	cd psiclj && clj -A:native-image

psiclj/psiclj.jar: psiclj/src/psiclj.clj out/main.js
	cd psiclj && clj -A:uberjar

run-clj:  out/main.js
	cd psiclj && PORT=3002 clj -m psiclj
run-bin:  psiclj/psiclj
	PORT=3003 psiclj/spiclj
run-jar: psiclj/psiclj.jar
	PORT=3004 java -cp psiclj/psiclj.jar clojure.main -m psiclj

debug-figwheel:
	clj -Sdeps "{:deps {com.bhauman/figwheel-main {:mvn/version \"0.2.14\"}}}"  -m figwheel.main --print-config -b dev
