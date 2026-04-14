"""
MinoLingo Face Recognition Microservice
========================================
Flask microservice using DeepFace (ArcFace backend) for face
registration and verification. Runs on port 5001.

Endpoints:
  POST /face/register/<userId>  — Register a face from base64 image
  POST /face/verify/<userId>    — Verify a live photo against stored face
  GET  /face/status/<userId>    — Check registration status
  DELETE /face/delete/<userId>  — Remove stored face data
  GET  /health                  — Health check
"""

import os
import base64
import json
import tempfile
import traceback
from io import BytesIO

import numpy as np
from flask import Flask, request, jsonify
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

# ── Configuration ──────────────────────────────────────────────
FACE_DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "face_data")
os.makedirs(FACE_DATA_DIR, exist_ok=True)

MODEL_NAME = "ArcFace"
DETECTOR_BACKENDS = ["opencv", "ssd", "mtcnn"]  # Fallback chain
DISTANCE_METRIC = "cosine"
# Stricter threshold for login auth. DeepFace default 0.68 is tuned for
# general recognition benchmarks and is too permissive for authentication —
# it lets look-alikes (and sometimes unrelated people under poor lighting)
# pass as the same person. 0.45 keeps same-person matches reliable while
# rejecting different people.
VERIFY_THRESHOLD = 0.45
# For identify (1-to-N), the best match must beat the runner-up by this
# margin. Prevents "closest wins" when no one truly matches.
IDENTIFY_MARGIN = 0.08

# Lazy-load DeepFace to speed up startup
_deepface = None


def get_deepface():
    """Lazy-import DeepFace so startup is fast and model downloads
    happen only when first needed."""
    global _deepface
    if _deepface is None:
        from deepface import DeepFace
        _deepface = DeepFace
        # Warm up the model by running a dummy representation
        print("[FaceService] Warming up ArcFace model...")
        try:
            dummy = np.zeros((64, 64, 3), dtype=np.uint8)
            tmp = tempfile.NamedTemporaryFile(suffix=".jpg", delete=False)
            from PIL import Image
            Image.fromarray(dummy).save(tmp.name)
            try:
                _deepface.represent(img_path=tmp.name, model_name=MODEL_NAME,
                                    detector_backend="skip", enforce_detection=False)
            except Exception:
                pass
            finally:
                os.unlink(tmp.name)
        except ImportError:
            pass
        print("[FaceService] ArcFace model ready.")
    return _deepface


def _embedding_path(user_id: str) -> str:
    return os.path.join(FACE_DATA_DIR, f"{user_id}.npy")


def _meta_path(user_id: str) -> str:
    return os.path.join(FACE_DATA_DIR, f"{user_id}_meta.json")


def _decode_base64_image(data: str) -> str:
    """Decode a base64 image string and save to a temp file.
    Returns the temp file path."""
    if "," in data:
        data = data.split(",", 1)[1]

    # Validate base64 data
    try:
        img_bytes = base64.b64decode(data)
    except Exception:
        raise ValueError("Invalid base64 image data.")

    if len(img_bytes) < 100:
        raise ValueError("Image data is too small — likely not a valid image.")

    tmp = tempfile.NamedTemporaryFile(suffix=".jpg", delete=False)
    tmp.write(img_bytes)
    tmp.close()
    return tmp.name


def _get_embedding(img_path: str):
    """Extract face embedding from image using fallback detector backends.
    Returns numpy array or raises ValueError."""
    DeepFace = get_deepface()

    last_error = None
    for backend in DETECTOR_BACKENDS:
        try:
            representations = DeepFace.represent(
                img_path=img_path,
                model_name=MODEL_NAME,
                detector_backend=backend,
                enforce_detection=True
            )
            if representations and len(representations) > 0:
                embedding = np.array(representations[0]["embedding"])
                print(f"[FaceService] Face detected using '{backend}' backend.")
                return embedding
        except Exception as e:
            last_error = e
            print(f"[FaceService] Backend '{backend}' failed: {e}")
            continue

    # All detector backends failed — try with enforce_detection=False as last resort
    try:
        representations = DeepFace.represent(
            img_path=img_path,
            model_name=MODEL_NAME,
            detector_backend="skip",
            enforce_detection=False
        )
        if representations and len(representations) > 0:
            print("[FaceService] Using 'skip' backend (no face detection).")
            return np.array(representations[0]["embedding"])
    except Exception as e:
        last_error = e

    raise ValueError(
        f"No face detected in the image. Please ensure your face is clearly visible, "
        f"well-lit, and centered in the frame. (Detail: {last_error})"
    )


# ── Routes ─────────────────────────────────────────────────────

@app.route("/face/register/<user_id>", methods=["POST"])
def register_face(user_id: str):
    """Register a user's face from a base64-encoded photo."""
    try:
        body = request.get_json(force=True)
        image_data = body.get("image")
        if not image_data:
            return jsonify({"error": "Missing 'image' field (base64)."}), 400

        img_path = _decode_base64_image(image_data)

        try:
            embedding = _get_embedding(img_path)
        finally:
            if os.path.exists(img_path):
                os.unlink(img_path)

        # Validate embedding quality (non-zero, reasonable magnitude)
        if np.all(embedding == 0):
            return jsonify({"error": "Could not extract a valid face embedding. Please try a clearer photo."}), 400

        norm = np.linalg.norm(embedding)
        if norm < 0.1:
            return jsonify({"error": "Face embedding quality is too low. Please try a better photo."}), 400

        # Save embedding
        np.save(_embedding_path(user_id), embedding)

        # Save metadata
        import datetime
        meta = {
            "userId": user_id,
            "registeredAt": datetime.datetime.utcnow().isoformat(),
            "model": MODEL_NAME,
            "embeddingSize": len(embedding),
            "embeddingNorm": float(norm)
        }
        with open(_meta_path(user_id), "w") as f:
            json.dump(meta, f)

        return jsonify({
            "success": True,
            "message": f"Face registered successfully for user {user_id}.",
            "embeddingSize": len(embedding)
        })

    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": f"Registration failed: {str(e)}"}), 500


@app.route("/face/verify/<user_id>", methods=["POST"])
def verify_face(user_id: str):
    """Verify a live photo against the stored face for a user."""
    try:
        emb_path = _embedding_path(user_id)
        if not os.path.exists(emb_path):
            return jsonify({"error": f"No face registered for user {user_id}."}), 404

        body = request.get_json(force=True)
        image_data = body.get("image")
        if not image_data:
            return jsonify({"error": "Missing 'image' field (base64)."}), 400

        img_path = _decode_base64_image(image_data)

        try:
            live_embedding = _get_embedding(img_path)
        finally:
            if os.path.exists(img_path):
                os.unlink(img_path)

        stored_embedding = np.load(emb_path)

        # Cosine similarity
        dot = np.dot(stored_embedding, live_embedding)
        norm_a = np.linalg.norm(stored_embedding)
        norm_b = np.linalg.norm(live_embedding)

        if norm_a == 0 or norm_b == 0:
            return jsonify({
                "error": "Invalid embedding detected. Please re-register your face.",
                "verified": False
            }), 400

        cosine_distance = 1 - (dot / (norm_a * norm_b))
        verified = cosine_distance <= VERIFY_THRESHOLD
        confidence = round(max(0, 1 - cosine_distance), 4)

        return jsonify({
            "verified": bool(verified),
            "confidence": confidence,
            "distance": round(float(cosine_distance), 4),
            "threshold": VERIFY_THRESHOLD
        })

    except ValueError as e:
        return jsonify({"error": str(e), "verified": False}), 400
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": f"Verification failed: {str(e)}", "verified": False}), 500


@app.route("/face/status/<user_id>", methods=["GET"])
def face_status(user_id: str):
    """Check if a user has a registered face."""
    emb_path = _embedding_path(user_id)
    meta_path_file = _meta_path(user_id)

    registered = os.path.exists(emb_path)
    meta = {}
    if registered and os.path.exists(meta_path_file):
        with open(meta_path_file, "r") as f:
            meta = json.load(f)

    return jsonify({
        "userId": user_id,
        "registered": registered,
        "registeredAt": meta.get("registeredAt"),
        "model": meta.get("model")
    })


@app.route("/face/delete/<user_id>", methods=["DELETE"])
def delete_face(user_id: str):
    """Remove stored face data for a user."""
    emb_path = _embedding_path(user_id)
    meta_path_file = _meta_path(user_id)

    deleted = False
    if os.path.exists(emb_path):
        os.remove(emb_path)
        deleted = True
    if os.path.exists(meta_path_file):
        os.remove(meta_path_file)

    if deleted:
        return jsonify({"success": True, "message": f"Face data deleted for user {user_id}."})
    else:
        return jsonify({"error": f"No face data found for user {user_id}."}), 404


@app.route("/face/identify", methods=["POST"])
def identify_face():
    """Identify a user by scanning all stored face embeddings.

    Body: { "image": "<base64>" }
    Returns: { "userId": "<id>", "confidence": 0.xx, "distance": 0.xx }
    or       { "error": "..." } if no match found.
    """
    try:
        body = request.get_json(force=True)
        image_data = body.get("image")
        if not image_data:
            return jsonify({"error": "Missing 'image' field (base64)."}), 400

        img_path = _decode_base64_image(image_data)

        try:
            live_embedding = _get_embedding(img_path)
        finally:
            if os.path.exists(img_path):
                os.unlink(img_path)

        # Scan all stored embeddings — track best + second-best for margin check
        best_user_id = None
        best_distance = float("inf")
        second_distance = float("inf")

        for filename in os.listdir(FACE_DATA_DIR):
            if not filename.endswith(".npy"):
                continue
            user_id = filename[:-4]  # strip ".npy"
            emb_path = os.path.join(FACE_DATA_DIR, filename)

            try:
                stored_embedding = np.load(emb_path)
            except Exception:
                continue

            norm_a = np.linalg.norm(stored_embedding)
            norm_b = np.linalg.norm(live_embedding)
            if norm_a == 0 or norm_b == 0:
                continue

            dot = np.dot(stored_embedding, live_embedding)
            cosine_distance = 1 - (dot / (norm_a * norm_b))

            if cosine_distance < best_distance:
                second_distance = best_distance
                best_distance = cosine_distance
                best_user_id = user_id
            elif cosine_distance < second_distance:
                second_distance = cosine_distance

        if best_user_id is None or best_distance > VERIFY_THRESHOLD:
            return jsonify({
                "error": "No matching face found. Please use password login or register your face."
            }), 404

        # Require clear margin vs runner-up so ambiguous faces are rejected
        if (second_distance - best_distance) < IDENTIFY_MARGIN:
            return jsonify({
                "error": "Face match is ambiguous. Please use password login."
            }), 404

        confidence = round(max(0, 1 - best_distance), 4)
        print(f"[FaceService] Identified user {best_user_id} (distance={best_distance:.4f})")
        return jsonify({
            "userId": best_user_id,
            "confidence": confidence,
            "distance": round(float(best_distance), 4)
        })

    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": f"Identification failed: {str(e)}"}), 500


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({"status": "ok", "model": MODEL_NAME})


# ── Entry Point ────────────────────────────────────────────────, 
if __name__ == "__main__":
    print("=" * 50)
    print("  MinoLingo Face Recognition Service")
    print(f"  Model: {MODEL_NAME} | Port: 5001")
    print("=" * 50)
    app.run(host="0.0.0.0", port=5001, debug=False)
