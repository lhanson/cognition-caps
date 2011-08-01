; Add . to classpath to pick up config properties
(require 'robert.hooke
         'leiningen.classpath)
(robert.hooke/add-hook #'leiningen.classpath/get-classpath
                       (fn [get-classpath project]
                         (conj (get-classpath project) ".")))
