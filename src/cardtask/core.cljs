(ns cardtask.core)
(enable-console-print!)
(set! *warn-on-infer* true)
(println "Hello world!")

(set! js/trials (clj->js {:timeline [{:type "html-keyboard-response", :stimulus "hi"}]}))
(js/jsPsych.init js/trials)

