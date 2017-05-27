(ns harold-paint.core
  (:require [reagent.core :as reagent]))

(enable-console-print!)

(def w 512)
(def h 512)

(defn rgba->int
  [r g b a]
  (bit-or (bit-shift-left r 24)
          (bit-shift-left g 16)
          (bit-shift-left b 8)
          a))

(defn rgb->int
  [r g b]
  (rgba->int r g b 0xff))

(defn- init-picture
  []
  (let [a (js/Uint8ClampedArray. (* w h))]
    (dotimes [i (* w h)]
      (aset a i (mod (+ i (js/Math.floor (* (rand) 32)) -64) 256)))
    a))

(defn- init-palette
  [offset]
  (let [ab (js/ArrayBuffer. (* 256 4))
        dv (js/DataView. ab)]
    (dotimes [i 256]
      (.setInt32 dv (* i 4) (rgb->int (mod (+ offset i) 256) 0 0)))
    (js/Int32Array. ab)))

(defonce state*
  (let [ab (js/ArrayBuffer. (* w h 4))
        dv (js/DataView. ab)
        id (js/ImageData. (js/Uint8ClampedArray. ab) w h)]
    (atom {:gl-init false
           :frame 0
           :picture (init-picture)
           :picture-array-buffer ab
           :picture-data-view dv
           :picture-image-data id
           :palette (init-palette 0)})))

(defonce ms* (reagent/atom 0))

(defn get-canvas
  []
  (js/document.getElementById "c"))

(defn page
  [ratom]
  [:div.page
   [:div "harold-paint " @ms* "ms/frame"]
   [:canvas {:id "c" :width w :height h}]
   [:canvas {:id "c2" :width w :height h}]])

(defn render
  []
  (let [ctx (.getContext (get-canvas) "2d")
        picture (:picture @state*)
        palette (:palette @state*)
        {dv :picture-data-view
         id :picture-image-data} @state* ]
    (dotimes [i (* w h)]
      (.setInt32 dv (* 4 i) (aget palette (aget picture i))))
    (.putImageData ctx id 0 0)))

(defn change-palette
  []
  (swap! state* update :frame inc)
  (swap! state* assoc :palette (init-palette (:frame @state*))))

(defn tick
  []
  (when-not (:gl-init @state*)
    (let [canvas (js/document.getElementById "c2")
          gl (.getContext canvas "webgl")]
      (.clearColor gl 1.0 0.0 0.0 1.0)
      (.clear gl (.-COLOR_BUFFER_BIT gl))
      (swap! state* assoc :gl-init true)))
  (let [start-time (.getTime (js/Date.))]
    (render)
    (change-palette)
    (reset! ms* (- (.getTime (js/Date.)) start-time)))
  (js/requestAnimationFrame tick))

(defn reload
  []
  (reagent/render [page state*]
                  (.getElementById js/document "app")))

(defn ^:export main []
  (reload)
  (js/requestAnimationFrame tick))
