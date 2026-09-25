#!/usr/bin/env python3
import argparse
import hashlib
import json
import shutil
import sys
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch
from huggingface_hub import snapshot_download
from safetensors.torch import load_file
from transformers import AutoTokenizer

QUESTIONS = [
    {
        "id": "intent",
        "t": "choice",
        "ins": "Welche Absicht hat der Nutzer?",
        "crit": {
            "chat": "allgemeine Unterhaltung oder Frage",
            "search": "Informationen aus dem Internet recherchieren",
            "device_action": "eine Aktion auf dem Android-Gerät ausführen",
            "unsafe": "unsichere oder schädliche Anweisung",
        },
    },
    {
        "id": "needs_search",
        "t": "noul",
        "ins": "Muss dafür eine Internetrecherche durchgeführt werden?",
        "crit": {"false": "nein", "true": "ja"},
    },
    {
        "id": "needs_action",
        "t": "noul",
        "ins": "Muss eine Aktion auf dem Android-Gerät ausgeführt werden?",
        "crit": {"false": "nein", "true": "ja"},
    },
    {
        "id": "blocked",
        "t": "noul",
        "ins": "Soll die Anweisung aus Sicherheitsgründen blockiert werden?",
        "crit": {"false": "nein", "true": "ja"},
    },
]


class ExportWrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask, marker_pos, marker_mask, qtype):
        logits, _ = self.model(input_ids, attention_mask, marker_pos, marker_mask, qtype)
        return logits


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="models")
    parser.add_argument("--cache", default=".cache/laya")
    parser.add_argument("--revision", default="d51a65072f7c8eab3c4186b6e062de63d0bd5303")
    args = parser.parse_args()
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    cache = Path(args.cache)
    cache.mkdir(parents=True, exist_ok=True)

    root = Path(snapshot_download(
        repo_id="convaiinnovations/laya",
        local_dir=str(cache / "laya"),
        revision=args.revision,
        allow_patterns=[
            "multilingual/model.safetensors",
            "multilingual/encoder/config.json",
            "multilingual/rl_agent_config.json",
            "multilingual/tokenizer/tokenizer.json",
            "multilingual/tokenizer/tokenizer_config.json",
            "rl_common.py",
        ],
    ))
    model_root = root / "multilingual"
    sys.path.insert(0, str(root))
    from rl_common import QTYPES, build_model, build_sequence, collate_items

    config = json.loads((model_root / "rl_agent_config.json").read_text())
    tokenizer = AutoTokenizer.from_pretrained(str(model_root / "tokenizer"))
    model = build_model(config, encoder_dir=str(model_root / "encoder"))
    model.load_state_dict(load_file(str(model_root / "model.safetensors")), strict=True)
    model.eval()

    items = []
    for question in QUESTIONS:
        sequence, markers = build_sequence(
            tokenizer,
            "Bitte öffne die Einstellungen und suche nach Wetter.",
            question,
            config["max_len"],
            config["head_max_len"],
        )
        items.append({
            "ids": sequence,
            "markers": markers,
            "qtype": QTYPES[question["t"]],
            "target": [0.0] * len(markers),
            "label": -1,
            "episode": 0,
            "episode_step": 0,
            "episode_len": 1,
            "source": "export",
        })
    batch = collate_items([items], tokenizer.pad_token_id)
    wrapper = ExportWrapper(model).eval()
    export_inputs = (
        batch["input_ids"],
        batch["attention_mask"],
        batch["marker_pos"],
        batch["marker_mask"],
        batch["qtype"],
    )
    torch.onnx.export(
        wrapper,
        export_inputs,
        str(output / "laya-multilingual.onnx"),
        input_names=["input_ids", "attention_mask", "marker_pos", "marker_mask", "qtype"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "sequence"},
            "attention_mask": {0: "batch", 1: "sequence"},
            "marker_pos": {0: "batch", 1: "markers"},
            "marker_mask": {0: "batch", 1: "markers"},
            "qtype": {0: "batch"},
            "logits": {0: "batch", 1: "markers"},
        },
        opset_version=18,
        do_constant_folding=True,
    )
    exported_model = onnx.load(output / "laya-multilingual.onnx")
    onnx.checker.check_model(exported_model)
    ort_session = ort.InferenceSession(str(output / "laya-multilingual.onnx"), providers=["CPUExecutionProvider"])
    ort_inputs = {
        "input_ids": batch["input_ids"].numpy(),
        "attention_mask": batch["attention_mask"].numpy(),
        "marker_pos": batch["marker_pos"].numpy(),
        "marker_mask": batch["marker_mask"].numpy(),
        "qtype": batch["qtype"].numpy(),
    }
    with torch.no_grad():
        expected = wrapper(*export_inputs).detach().cpu().numpy()
    actual = ort_session.run(["logits"], ort_inputs)[0]
    np.testing.assert_allclose(actual, expected, rtol=1e-3, atol=1e-3)
    shutil.copy2(model_root / "tokenizer" / "tokenizer.json", output / "laya-tokenizer.json")
    metadata = {
        "model": "convaiinnovations/laya",
        "revision": args.revision,
        "subfolder": "multilingual",
        "input_names": ["input_ids", "attention_mask", "marker_pos", "marker_mask", "qtype"],
        "output_name": "logits",
        "max_length": config["max_len"],
        "head_max_length": config["head_max_len"],
        "questions": QUESTIONS,
        "sha256": {
            "laya-multilingual.onnx": sha256(output / "laya-multilingual.onnx"),
            "laya-tokenizer.json": sha256(output / "laya-tokenizer.json"),
        },
    }
    (output / "laya-router.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2))
    print(output / "laya-multilingual.onnx")


if __name__ == "__main__":
    main()
