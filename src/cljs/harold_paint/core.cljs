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

(def v-shader
  "attribute vec4 a_position;
  void main() {
    gl_Position = a_position;
  }")

(def f-shader
  "precision mediump float;
  void main() {
    gl_FragColor = vec4(0, 0, 0.5, 1);
  }")

(defn create-shader
  [gl type source]
  (let [shader (.createShader gl type)]
    (.shaderSource gl shader source)
    (.compileShader gl shader)
    (if (.getShaderParameter gl shader (.-COMPILE_STATUS gl))
      shader
      (do (println (.getShaderInfoLog gl shader))
          (.deleteShader gl shader)))))

(defn create-program
  [gl vs fs]
  (let [program (.createProgram gl)]
    (.attachShader gl program vs)
    (.attachShader gl program fs)
    (.linkProgram gl program)
    (if (.getProgramParameter gl program (.-LINK_STATUS gl))
      program
      (do (println (.getProgramLogInfo gl program))
          (.deleteProgram program)))))

(defn tick
  []
  (when-not (:gl-init @state*)
    (let [canvas (js/document.getElementById "c2")
          gl (.getContext canvas "webgl")
          vs (create-shader gl (.-VERTEX_SHADER gl) v-shader)
          fs (create-shader gl (.-FRAGMENT_SHADER gl) f-shader)
          program (create-program gl vs fs)
          positionAttributeLocation (.getAttribLocation gl program "a_position")
          positionBuffer (.createBuffer gl)
          _ (.bindBuffer gl (.-ARRAY_BUFFER gl) positionBuffer)
          positions #js [0 0, 0 0.5, 0.7 0]]
      (.bufferData gl (.-ARRAY_BUFFER gl) (js/Float32Array. positions) (.-STATIC_DRAW gl))
      (.viewport gl 0 0 (.-width canvas) (.-height canvas))
      (.clearColor gl 1.0 0.0 0.0 1.0)
      (.clear gl (.-COLOR_BUFFER_BIT gl))
      (.useProgram gl program)
      (.enableVertexAttribArray gl positionAttributeLocation)
      (.vertexAttribPointer gl positionAttributeLocation 2 (.-FLOAT gl) false 0 0)
      (.drawArrays gl (.-TRIANGLES gl) 0 3)
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
