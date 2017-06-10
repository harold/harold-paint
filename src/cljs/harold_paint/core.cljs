(ns harold-paint.core
  (:require [reagent.core :as reagent]
            [harold-paint.color-picker :refer [color-picker]]))

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
           :gl nil
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
   [:canvas {:id "c2" :width w :height h}]
   [color-picker {:default-value [128 128 255]
                  :on-change #(println %)}]])

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
  (swap! state* assoc :palette (init-palette (:frame @state*))))

(def v-shader
  "attribute vec4 a_position;
  varying vec2 v_texcoord;
  void main() {
    gl_Position = a_position;
    gl_Position.x = (a_position.x - 0.5) * 2.0;
    gl_Position.y = (a_position.y - 0.5) * -2.0;
    v_texcoord = a_position.xy;
  }")

(def f-shader
  "precision mediump float;
  varying vec2 v_texcoord;
  uniform sampler2D u_image;
  uniform sampler2D u_palette;
  void main() {
    float index = texture2D(u_image, v_texcoord).a * 255.0;
    gl_FragColor = texture2D(u_palette, vec2((index+0.5)/256.0, 0.5));
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

(defn init-texture
  [gl unit texture]
  (.activeTexture gl unit)
  (.bindTexture gl (.-TEXTURE_2D gl) texture)
  (.texParameteri gl (.-TEXTURE_2D gl) (.-TEXTURE_WRAP_S gl) (.-CLAMP_TO_EDGE gl))
  (.texParameteri gl (.-TEXTURE_2D gl) (.-TEXTURE_WRAP_T gl) (.-CLAMP_TO_EDGE gl))
  (.texParameteri gl (.-TEXTURE_2D gl) (.-TEXTURE_MIN_FILTER gl) (.-NEAREST gl))
  (.texParameteri gl (.-TEXTURE_2D gl) (.-TEXTURE_MAG_FILTER gl) (.-NEAREST gl)))

(defn tick
  []
  (swap! state* update :frame inc)
  (when-not (:gl-init @state*)
    (let [canvas (js/document.getElementById "c2")
          gl (.getContext canvas "webgl")
          _ (swap! state* assoc :gl gl)
          vs (create-shader gl (.-VERTEX_SHADER gl) v-shader)
          fs (create-shader gl (.-FRAGMENT_SHADER gl) f-shader)
          program (create-program gl vs fs)
          positionLoc (.getAttribLocation gl program "a_position")
          imageLoc (.getUniformLocation gl program "u_image")
          paletteLoc (.getUniformLocation gl program "u_palette")
          positionBuffer (.createBuffer gl)
          _ (.bindBuffer gl (.-ARRAY_BUFFER gl) positionBuffer)
          positions #js [0 0, 1 1, 0 1
                         0 0, 1 0, 1 1]
          paletteTex (.createTexture gl)
          imageTex (.createTexture gl)
          image (js/Uint8Array. (for [y (range h)
                                      x (range w)]
                                  (mod (+ x (rand-int 40) -20) 256)))]
      ;; image
      (init-texture gl (.-TEXTURE0 gl) imageTex)
      (.texImage2D gl (.-TEXTURE_2D gl) 0 (.-ALPHA gl) w h 0 (.-ALPHA gl) (.-UNSIGNED_BYTE gl) image)
      ;; palette
      (init-texture gl (.-TEXTURE1 gl) paletteTex)
      ;; verts
      (.bufferData gl (.-ARRAY_BUFFER gl) (js/Float32Array. positions) (.-STATIC_DRAW gl))
      (.viewport gl 0 0 (.-width canvas) (.-height canvas))
      (.clearColor gl 0.0 1.0 0.0 1.0)
      (.clear gl (.-COLOR_BUFFER_BIT gl))
      (.useProgram gl program)
      (.uniform1i gl imageLoc 0)
      (.uniform1i gl paletteLoc 1)
      (.enableVertexAttribArray gl positionLoc)
      (.vertexAttribPointer gl positionLoc 2 (.-FLOAT gl) false 0 0)
      (swap! state* assoc :gl-init true)))
  (let [gl (:gl @state*)
        frame (:frame @state*)
        palette (js/Uint8Array. (->> (for [i (range 256)]
                                       [(mod (+ frame i) 256) 0 0 255])
                                     (apply concat)))]
    (.texImage2D gl (.-TEXTURE_2D gl) 0 (.-RGBA gl) 256 1 0 (.-RGBA gl) (.-UNSIGNED_BYTE gl) palette)
    (.drawArrays gl (.-TRIANGLES gl) 0 6))
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
