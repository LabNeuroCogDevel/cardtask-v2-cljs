out/main.js: src/cardtask/core.cljs
	clj -M --main cljs.main --optimizations advanced -c cardtask.core
psiclj/psiclj: psiclj/src/psiclj.clj
	# sudo archlinux-java set java-11-graalvm
	cd psiclj && clj -A:native-image

