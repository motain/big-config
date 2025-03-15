(ns com.bunimo.clansi
  (:require [clojure.string :as str]))

(def ATTRIBUTES (atom {:error [4 91 40]
                       :clear 0
                       :reset 0
                       :normal 0
                       :bold 1
                       :dark 2
                       :faint 2
                       :dim 2
                       :italic 3
                       :underline 4
                       :underscore 4
                       :blink 5
                       :reverse 7
                       :concealed 8

                       :black 30 :on-black 40 :bg-black 40
                       :red 31 :on-red 41 :bg-red 41
                       :green 32 :on-green 42 :bg-green 42
                       :yellow 33 :on-yellow 43 :bg-yellow 43
                       :blue 34 :on-blue 44 :bg-blue 44
                       :magenta 35 :on-magenta 45 :bg-magenta 45
                       :cyan 36 :on-cyan 46 :bg-cyan 46
                       :white 37 :on-white 47 :bg-white 47
                       :default 39 :on-default 49 :bg-default 49

                       :bright-black 90 :lt-black 90 :on-bright-black 100 :on-lt-black 100 :bg-bright-black 100 :bg-lt-black 100
                       :bright-red 91 :lt-red 91 :on-bright-red 101 :on-lt-red 101 :bg-bright-red 101 :bg-lt-red 101
                       :bright-green 92 :lt-green 92 :on-bright-green 102 :on-lt-green 102 :bg-bright-green 102 :bg-lt-green 102
                       :bright-yellow 93 :lt-yellow 93 :on-bright-yellow 103 :on-lt-yellow 103 :bg-bright-yellow 103 :bg-lt-yellow 103
                       :bright-blue 94 :lt-blue 94 :on-bright-blue 104 :on-lt-blue 104 :bg-bright-blue 104 :bg-lt-blue 104
                       :bright-magenta 95 :lt-magenta 95 :on-bright-magenta 105 :on-lt-magenta 105 :bg-bright-magenta 105 :bg-lt-magenta 105
                       :bright-cyan 96 :lt-cyan 96 :on-bright-cyan 106 :on-lt-cyan 106 :bg-bright-cyan 106 :bg-lt-cyan 106
                       :bright-white 97 :lt-white 97 :on-bright-white 107 :on-lt-white 107 :bg-bright-white 107 :bg-lt-white 107}))

;; dumby, none of what follows modifies ATTRIBUTES  (Clojure collections are immutable, remember)
(defn- setup-grey-attrs []
  (doseq [code (range 0 24)]
    (swap! ATTRIBUTES assoc (keyword (format "grey%02d" code)) [38 5 (+ code 232)])
    (swap! ATTRIBUTES assoc (keyword (format "on-grey%02d" code)) [48 5 (+ code 232)])))

(defn- setup-rgb-attrs []
  (doseq [r (range 0 6)
          g (range 0 6)
          b (range 0 6)]
    (let [name (str "rgb" r g b)
          on-name (str "on-rgb" r g b)
          code (+ 16 (* 36 r) (* 6 g) b)]
      (swap! ATTRIBUTES assoc (keyword name) [38 5 code])
      (swap! ATTRIBUTES assoc (keyword on-name) [48 5 code]))))

(defn- setup-ansi-attrs []
  (doseq [code (range 0 256)]
    (swap! ATTRIBUTES assoc (keyword (str "ansi" code)) [38 5 code])
    (swap! ATTRIBUTES assoc (keyword (str "on-ansi" code)) [48 5 code])))

(defn- setup-attrs []
  (setup-grey-attrs)
  (setup-ansi-attrs)
  (setup-rgb-attrs))

(setup-attrs)

(def ^:dynamic *use-ansi* "Rebind this to false if you don't want to see ANSI codes in some part of your code." true)

(defn- sgr [& codes]
  (str "\u001b[" (str/join ";" (flatten codes)) "m"))

(defn- valid-style? [style]
  (some? (@ATTRIBUTES style)))

(defn- valid-styles
  "return a list of only the valid styles"
  [& styles]
  (filter #(valid-style? %) (flatten styles)))

(defn- all-valid-styles?
  "return true if all styles valid, else false"
  [& styles]
  (reduce (fn [x y] (and x (valid-style? y))) true (flatten styles)))

(defn ansi
  "Output an ANSI escape code using a style key.

   (ansi :blue)
   (ansi :underline)
   (ansi :underline :red :on-yellow)

  Note, try (style-test-page) to see all available styles.

  If *use-ansi* is bound to false, outputs an empty string instead of an
  ANSI code. You can use this to temporarily or permanently turn off
  ANSI color in some part of your program, while maintaining only 1
  version of your marked-up text.
  "
  [& styles]
  (if *use-ansi*
    (sgr
     (if (all-valid-styles? styles)
       (map @ATTRIBUTES (flatten styles))
       (:error @ATTRIBUTES)))
    ""))

(defmacro without-ansi
  "Runs the given code with the use-ansi variable temporarily bound to
  false, to suppress the production of any ANSI color codes specified
  in the code."
  [& code]
  `(binding [*use-ansi* false]
     ~@code))

(defmacro with-ansi
  "Runs the given code with the use-ansi variable temporarily bound to
  true, to enable the production of any ANSI color codes specified in
  the code."
  [& code]
  `(binding [*use-ansi* true]
     ~@code))

(defn style
  "Applies ANSI color and style to a text string.

   (style \"foo\" :red)
   (style \"foo\" :red :underline)
   (style \"foo\" :red :on-blue :underline)"
  [s & styles]
  (str (ansi styles) s (ansi :reset)))

(defn wrap-style
  "Wraps a base string with a stylized wrapper.
  If the wrapper is a string it will be placed on both sides of the base,
  and if it is a seq the first and second items will wrap the base.

  To wrap debug with red brackets => [debug]:

  (wrap-style \"debug\" [\"[\" \"]\"] :red)
  "
  [s wrapper & styles]
  (if (coll? wrapper)
    (str (style (first wrapper) styles) s (style (second wrapper) styles))
    (str (style wrapper styles) s (style wrapper styles))))

(defn style-test-page
  "Print the list of supported ANSI styles, each style name shown
  with its own style."
  []
  (doall
   (map #(println (style (name %) %)) (sort-by name (keys @ATTRIBUTES))))
  nil)

(def doc-style* (ref {:line  :blue
                      :title :bright
                      :args  :red
                      :macro :blue
                      :doc   :green}))

(defn print-special-doc-color
  "Print stylized special form documentation."
  [name type anchor]
  (println (style "-------------------------" (:line @doc-style*)))
  (println (style name (:title @doc-style*)))
  (println type)
  (println (style (str "  Please see http://clojure.org/special_forms#" anchor)
                  (:doc @doc-style*))))

(defn print-namespace-doc-color
  "Print stylized documentation for a namespace."
  [nspace]
  (println (style "-------------------------"    (:line @doc-style*)))
  (println (style (str (ns-name nspace))         (:title @doc-style*)))
  (println (style (str " " (:doc (meta nspace))) (:doc @doc-style*))))

(defn print-doc-color
  "Print stylized function documentation."
  [v]
  (println (style "-------------------------" (:line @doc-style*)))
  (println (style (str (ns-name (:ns (meta v))) "/" (:name (meta v)))
                  (:title @doc-style*)))
  (print "(")
  (doseq [alist (:arglists (meta v))]
    (print "[" (style (apply str (interpose " " alist)) (:args @doc-style*)) "]"))
  (println ")")

  (when (:macro (meta v))
    (println (style "Macro" (:macro @doc-style*))))
  (println "  " (style (:doc (meta v)) (:doc @doc-style*))))

(defmacro color-doc
  "A stylized version of clojure.core/doc."
  [v]
  `(binding [print-doc print-doc-color
             print-special-doc print-special-doc-color
             print-namespace-doc print-namespace-doc-color]
     (doc ~v)))

(defn colorize-docs
  []
  (intern 'clojure.core 'print-doc print-doc-color)
  (intern 'clojure.core 'print-special-doc print-special-doc-color)
  (intern 'clojure.core 'print-namespace-doc print-namespace-doc-color))

(defn angle->rgb [angle] (condp >= (mod angle 360)
                           60  [255 (- (* 4.25 angle) 0.01) 0]
                           120 [(- (* (- 120.0 angle) 4.25) 0.01) 255 0]
                           180 [0 255 (- (* (- angle 120.0) 4.25) 0.01)]
                           240 [0 (- (* (- 240.0 angle) 4.25) 0.01) 255]
                           300 [(- (* (- angle 240.0) 4.25) 0.01) 0 255]
                           360 [255 0 (- (* (- 360.0 angle) 4.25) 0.01)]))

(defn- print-sample []
  (let [base-colors ["black" "red" "green" "yellow" "blue" "magenta" "cyan" "white"]]
    (println (style "foregrounds:" :underline))
    (doseq [color base-colors]
      (print (format "%-10s" color))
      (print " |")
      (print (style " normal dark" (keyword color) :dark))
      (print " |")
      (print (style " bright dark" (keyword (str "bright-" color)) :dark))
      (print " |")
      (print (style " normal" (keyword color)))
      (print " |")
      (print (style " bold normal" (keyword color) :bold))
      (print " |")
      (print (style " bright" (keyword (str "bright-" color))))
      (print " |")
      (print (style " bright bold" (keyword (str "bright-" color)) :bold))
      (println " |"))
    (println)
    (println (style "backgrounds w/ bright white letters:" :underline))
    (doseq [color base-colors]
      (print (format "%-10s" color))
      (print " |")
      (print (style " normal dark" :bright-white :dark (keyword (str "on-" color))))
      (print " |")
      (print (style " bright dark" :bright-white :dark (keyword (str "on-bright-" color))))
      (print " |")
      (print (style " normal" :bright-white (keyword (str "on-" color))))
      (print " |")
      (print (style " bold normal" :bright-white :bold (keyword (str "on-" color))))
      (print " |")
      (print (style " bright" :bright-white (keyword (str "on-bright-" color))))
      (print " |")
      (print (style " bright bold" :bright-white :bold (keyword (str "on-bright-" color))))
      (println " |"))
    (println)
    (println (style "backgrounds w/ black letters:" :underline))
    (doseq [color base-colors]
      (print (format "%-10s" color))
      (print " |")
      (print (style " normal dark" :black (keyword (str "on-" color)) :dark))
      (print " |")
      (print (style " bright dark" :black (keyword (str "on-bright-" color)) :dark))
      (print " |")
      (print (style " normal" :black (keyword (str "on-" color))))
      (print " |")
      (print (style " bold normal" :black (keyword (str "on-" color)) :bold))
      (print " |")
      (print (style " bright" :black (keyword (str "on-bright-" color))))
      (print " |")
      (print (style " bright bold" :black (keyword (str "on-bright-" color)) :bold))
      (println " |"))
    (println)
    (println (style "greys:" :underline))
    (doseq [n (range 0 24)]
      (let [tag (format "grey-%02d" n)]
        (print (style (str "** " tag " **") (keyword (format "grey%02d" n))))
        (print " | ")
        (print (style (str "** " tag " **") :bright-white (keyword (format "on-grey%02d" n))))
        (print " | ")
        (print (style (str "** " tag " **") :black (keyword (format "on-grey%02d" n))))
        (println " | ")))
    (println)
    (println (style "ANSI:" :underline))
    (doseq [n (range 0 16)]
      (let [tag (format "ansi-%d" n)]
        (println (style (str "** " tag " **") (keyword (format "ansi%d" n))))))
    (println)))

(defn print-standard-color-box []
  (println (style "                 40m     41m     42m     43m     44m     45m     46m     47m   " :reset))
  (let [fg [:normal :bold :black [:bold :black] :red [:bold :red] :green [:bold :green] :yellow [:bold :yellow]
            :blue [:bold :blue] :magenta [:bold :magenta] :cyan [:bold :cyan] :white [:bold :white]]
        row-label ["     m ", "    1m ", "   30m ", " 1;30m ", "   31m ", " 1;31m ", "   32m ", " 1;32m ", "   33m ", " 1;33m ",
                   "   34m ", " 1;34m ", "   35m ", " 1;35m ", "   36m ", " 1;36m ", "   37m ", " 1;37m "]]
    (doseq [row (map (fn [color label] {:fg color :label label}) fg row-label)]
      (print (style (row :label) :normal))
      (doseq [bg [[] :bg-black :bg-red :bg-green :bg-yellow :bg-blue :bg-magenta :bg-cyan :bg-white]]
        (let [tag "  gYw  "]
          (print (style tag (row :fg) bg))
          (print (style " " :normal))))
      (println))))

(defn- print-rainbow []
  (doseq [step (range 0 30)]
    (let [angle (* step 12.0)
          [red green blue] (angle->rgb angle)
          r (Math/round (double (/ red 51)))
          g (Math/round (double (/ green 51)))
          b (Math/round (double (/ blue 51)))
          color (format "rgb%d%d%d" r g b)
          tag (format "#%d%d%d" r g b)]
      (print (style tag (keyword color))))))

(defn -main []
  ;; modes
  (println (style "modes:" :underline))
  (println (str "normal:  " (style ":normal :reset :clear"  :reset)))
  (println (str "bold:    " (style ":bold" :bold)))
  (println (str "faint:   " (style ":dim :dark :faint" :dim)))
  (println (str "italic:  " (style ":italic" :italic)))
  (println (str "under_:  " (style ":underline :underscore" :underscore)))
  (println (str "blink:   " (style ":blink" :blink)))
  (println (str "reverse: " (style ":reverse" :reverse)))
  (println (str "conceal: " (style ":concealed" :concealed)))
  (println (str "error    " (style ":trigger-an-error" :trigger-an-error)))
  (println)

  (print-sample)

  (println (style "Theme Color View:" :underline))
  (print-standard-color-box)

  (println (style "rainbow:" :underline))
  (print-rainbow)
  (println))
