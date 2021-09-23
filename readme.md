
# Demo
 * running on github pages https://labneurocogdevel.github.io/cardtask-v2-cljs/
 * heroku https://habittask.herokuapp.com/ $id/habittask/$tp/$run (very slow startup!)

# TODO
  * variable isi iti
  * fixed scheduling

# dev
initial project looks like
```
.
├── deps.edn # {:deps {org.clojure/clojurescript {:mvn/version "1.10.758"}}}
└── src
    └── cardtask
        └── core.cljs
```

## edit
emacs with cider (`cider-jack-in-cljs`)

## static pages
symlinked
 * out -> docs (can point gh pages there)
 * audio and style from resources/public/ into out

