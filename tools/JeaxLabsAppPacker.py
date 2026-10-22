#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""JeaxLabs App Packer: empacota APKs em .jxa com AES-256-GCM e assinatura Ed25519."""

from __future__ import annotations

import base64
import hashlib
import json
import os
import threading
import zipfile
from datetime import datetime, timezone
from pathlib import Path
import tkinter as tk
from tkinter import filedialog, messagebox, ttk, simpledialog

try:
    from cryptography.exceptions import InvalidSignature
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
    from cryptography.hazmat.primitives import serialization
except ImportError as exc:
    raise SystemExit("Falta a biblioteca cryptography. Instale com: py -m pip install cryptography") from exc


APP_NAME = "JeaxLabs App Packer"
FORMAT_VERSION = 1
KDF_ROUNDS = 600_000
BG = "#100D20"
PANEL = "#1D1833"
PANEL_2 = "#292044"
PURPLE = "#8B5CF6"
WHITE = "#F7F3FF"
MUTED = "#B8AFCF"
GREEN = "#34D399"
RED = "#FB7185"


def canonical_json(value: dict) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def derive_key(passphrase: str, salt: bytes) -> bytes:
    if len(passphrase) < 8:
        raise ValueError("A senha de criptografia precisa ter pelo menos 8 caracteres.")
    kdf = PBKDF2HMAC(algorithm=hashes.SHA256(), length=32, salt=salt, iterations=KDF_ROUNDS)
    return kdf.derive(passphrase.encode("utf-8"))


def load_private_key(path: Path, password: str) -> Ed25519PrivateKey:
    key_data = path.read_bytes()
    key = serialization.load_pem_private_key(key_data, password=password.encode("utf-8") if password else None)
    if not isinstance(key, Ed25519PrivateKey):
        raise ValueError("O arquivo selecionado não contém uma chave privada Ed25519.")
    return key


def public_pem(key: Ed25519PrivateKey) -> bytes:
    return key.public_key().public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )


def create_certificate(folder: Path, password: str) -> tuple[Path, Path, str]:
    if len(password) < 8:
        raise ValueError("Use uma senha com pelo menos 8 caracteres para proteger a chave privada.")
    folder.mkdir(parents=True, exist_ok=True)
    private = Ed25519PrivateKey.generate()
    pub = public_pem(private)
    fingerprint = sha256_bytes(private.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    ))
    private_path = folder / "JeaxLabs-Publisher-Private.pem"
    public_path = folder / "JeaxLabs-Publisher-Public.pem"
    private_path.write_bytes(private.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.BestAvailableEncryption(password.encode("utf-8")),
    ))
    public_path.write_bytes(pub)
    cert_info = {
        "schema_version": 1,
        "publisher": "JeaxLabs",
        "algorithm": "Ed25519",
        "fingerprint_sha256": fingerprint,
        "public_key_pem": pub.decode("ascii"),
        "created_utc": datetime.now(timezone.utc).isoformat(),
        "note": "A chave privada é secreta. O verificador precisa confiar neste fingerprint por um canal separado.",
    }
    (folder / "JeaxLabs-Publisher-Certificate.json").write_text(
        json.dumps(cert_info, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    return private_path, public_path, fingerprint


def encrypt_apk(apk_path: Path, output_path: Path, app_name: str, version: str,
                encrypt_password: str, private_key_path: Path, signing_password: str) -> tuple[str, str]:
    if not apk_path.is_file() or apk_path.suffix.lower() != ".apk":
        raise ValueError("Selecione um arquivo APK válido.")
    if not app_name.strip() or not version.strip():
        raise ValueError("Preencha o nome e a versão do aplicativo.")
    private_key = load_private_key(private_key_path, signing_password)
    pub = public_pem(private_key)
    public_der = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    fingerprint = sha256_bytes(public_der)
    salt = os.urandom(16)
    nonce = os.urandom(12)
    key = derive_key(encrypt_password, salt)
    plaintext = apk_path.read_bytes()
    aad = ("JeaxLabs-JXA-v1|" + app_name.strip() + "|" + version.strip()).encode("utf-8")
    ciphertext = AESGCM(key).encrypt(nonce, plaintext, aad)
    manifest = {
        "format": "JXA",
        "format_version": FORMAT_VERSION,
        "product": "JeaxLabs OS",
        "package_type": "android-apk",
        "name": app_name.strip(),
        "version": version.strip(),
        "source_filename": apk_path.name,
        "payload_filename": "payload.aesgcm",
        "encryption": "AES-256-GCM",
        "kdf": "PBKDF2-HMAC-SHA256",
        "kdf_iterations": KDF_ROUNDS,
        "salt_hex": salt.hex(),
        "nonce_hex": nonce.hex(),
        "aad_utf8": aad.decode("utf-8"),
        "payload_sha256": sha256_bytes(ciphertext),
        "payload_size": len(ciphertext),
        "signature": "Ed25519",
        "publisher_fingerprint_sha256": fingerprint,
        "created_utc": datetime.now(timezone.utc).isoformat(),
    }
    manifest_bytes = canonical_json(manifest)
    signature = private_key.sign(manifest_bytes)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("manifest.json", manifest_bytes)
        archive.writestr("payload.aesgcm", ciphertext, compress_type=zipfile.ZIP_STORED)
        archive.writestr("signature.ed25519", signature)
        archive.writestr("publisher.pub.pem", pub)
    private_key.public_key().verify(signature, manifest_bytes)
    return fingerprint, sha256_bytes(output_path.read_bytes())


class AppPacker(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("JeaxLabs • App Packer")
        self.geometry("900x740")
        self.minsize(820, 680)
        self.configure(bg=BG)
        self.apk = tk.StringVar()
        self.output = tk.StringVar(value=str(Path.home() / "Desktop"))
        self.app_name = tk.StringVar()
        self.version = tk.StringVar(value="1.0.0")
        self.encrypt_password = tk.StringVar()
        self.signing_key = tk.StringVar()
        self.key_password = tk.StringVar()
        self.status = tk.StringVar(value="Pronto para empacotar.")
        self._build()

    def _build(self):
        style = ttk.Style(self)
        style.theme_use("clam")
        style.configure("TEntry", fieldbackground=PANEL_2, foreground=WHITE, insertcolor=WHITE)
        style.configure("TCombobox", fieldbackground=PANEL_2, foreground=WHITE)
        header = tk.Frame(self, bg=BG)
        header.pack(fill="x", padx=26, pady=(22, 12))
        tk.Label(header, text="JX", font=("Segoe UI Black", 27), fg=WHITE, bg=PURPLE,
                 width=3, height=1).pack(side="left", padx=(0, 14))
        title = tk.Frame(header, bg=BG)
        title.pack(side="left", fill="x", expand=True)
        tk.Label(title, text="JEAXLABS APP PACKER", font=("Segoe UI", 20, "bold"),
                 fg=WHITE, bg=BG).pack(anchor="w")
        tk.Label(title, text="APK  →  .jxa criptografado e assinado para o JeaxLabs OS",
                 font=("Segoe UI", 10), fg=MUTED, bg=BG).pack(anchor="w", pady=(3, 0))
        tk.Label(self, text="CRIPTOGRAFIA AES-256-GCM  •  ASSINATURA ED25519",
                 font=("Segoe UI", 9, "bold"), fg=GREEN, bg=BG).pack(anchor="w", padx=28, pady=(0, 10))

        canvas = tk.Frame(self, bg=PANEL, highlightbackground="#3B3156", highlightthickness=1)
        canvas.pack(fill="both", expand=True, padx=26, pady=(0, 16))
        self._field(canvas, "APK de origem", self.apk, self._browse_apk, 0)
        self._field(canvas, "Nome do aplicativo", self.app_name, None, 1)
        self._field(canvas, "Versão", self.version, None, 2)
        self._field(canvas, "Pasta de saída", self.output, self._browse_output, 3)
        self._field(canvas, "Chave privada Ed25519 (.pem)", self.signing_key, self._browse_key, 4)
        self._field(canvas, "Senha da chave privada", self.key_password, None, 5, secret=True)
        self._field(canvas, "Senha de criptografia do pacote", self.encrypt_password, None, 6, secret=True)

        buttons = tk.Frame(canvas, bg=PANEL)
        buttons.grid(row=7, column=0, columnspan=3, sticky="ew", padx=22, pady=(14, 8))
        tk.Button(buttons, text="＋  CRIAR CERTIFICADO", command=self._create_cert,
                  bg=PANEL_2, fg=WHITE, activebackground=PURPLE, activeforeground=WHITE,
                  relief="flat", font=("Segoe UI", 10, "bold"), padx=15, pady=13).pack(side="left", padx=(0, 10))
        tk.Button(buttons, text="EMPACOTAR .JXA", command=self._start_pack,
                  bg=PURPLE, fg=WHITE, activebackground="#7040D5", activeforeground=WHITE,
                  relief="flat", font=("Segoe UI", 11, "bold"), padx=25, pady=13).pack(side="left")
        tk.Label(canvas, text="Guarde a chave privada e suas senhas fora do repositório público.\nO APK receptor deve confiar no fingerprint do certificado por um canal independente.",
                 font=("Segoe UI", 9), fg=MUTED, bg=PANEL, justify="left").grid(
                    row=8, column=0, columnspan=3, sticky="w", padx=24, pady=(8, 16))
        footer = tk.Frame(self, bg=BG)
        footer.pack(fill="x", padx=28, pady=(0, 18))
        tk.Label(footer, textvariable=self.status, font=("Segoe UI", 10), fg=WHITE, bg=BG).pack(side="left")
        tk.Label(footer, text="JeaxLabs Security Tools", font=("Segoe UI", 9), fg=MUTED, bg=BG).pack(side="right")

    def _field(self, parent, label, variable, browse, row, secret=False):
        tk.Label(parent, text=label, font=("Segoe UI", 10, "bold"), fg=MUTED, bg=PANEL).grid(
            row=row, column=0, sticky="w", padx=(22, 14), pady=8)
        entry = ttk.Entry(parent, textvariable=variable, show="•" if secret else "")
        entry.grid(row=row, column=1, sticky="ew", padx=4, pady=8, ipady=6)
        if browse:
            tk.Button(parent, text="Procurar", command=browse, bg=PANEL_2, fg=WHITE,
                      activebackground=PURPLE, relief="flat", padx=12).grid(row=row, column=2, padx=(7, 22), pady=8)
        parent.grid_columnconfigure(1, weight=1)

    def _browse_apk(self):
        path = filedialog.askopenfilename(title="Selecione o APK", filetypes=[("Android APK", "*.apk"), ("Todos", "*.*")])
        if path:
            self.apk.set(path)
            if not self.app_name.get():
                self.app_name.set(Path(path).stem.replace("_", " "))

    def _browse_output(self):
        path = filedialog.askdirectory(title="Pasta de saída")
        if path:
            self.output.set(path)

    def _browse_key(self):
        path = filedialog.askopenfilename(title="Chave privada do publicador", filetypes=[("PEM", "*.pem"), ("Todos", "*.*")])
        if path:
            self.signing_key.set(path)

    def _create_cert(self):
        folder = filedialog.askdirectory(title="Onde salvar o certificado JeaxLabs")
        if not folder:
            return
        password = simpledialog.askstring("Proteger chave privada", "Crie uma senha para a chave privada (mín. 8 caracteres):", show="•", parent=self)
        if not password:
            return
        try:
            private, public, fingerprint = create_certificate(Path(folder), password)
            self.signing_key.set(str(private))
            self.key_password.set(password)
            messagebox.showinfo("Certificado criado", f"Certificado criado e chave privada protegida.\nFingerprint SHA-256:\n{fingerprint}\n\nGuarde a chave privada em local seguro. A chave pública e o JSON de certificado podem ser distribuídos.", parent=self)
            self.status.set("Certificado Ed25519 criado.")
        except Exception as exc:
            messagebox.showerror("Falha ao criar certificado", str(exc), parent=self)

    def _start_pack(self):
        apk = self.apk.get().strip()
        name = self.app_name.get().strip()
        version = self.version.get().strip()
        password = self.encrypt_password.get()
        key_path = self.signing_key.get().strip()
        key_password = self.key_password.get()
        out_dir = self.output.get().strip()
        if not all([apk, name, version, password, key_path, out_dir]):
            messagebox.showwarning("Campos incompletos", "Preencha APK, nome, versão, pasta, certificado e senha de criptografia.", parent=self)
            return
        if len(password) < 8:
            messagebox.showwarning("Senha curta", "Use pelo menos 8 caracteres na senha de criptografia.", parent=self)
            return
        target = Path(out_dir) / f"{name.strip().replace(' ', '-')}-{version}.jxa"
        self.status.set("Criptografando e assinando…")
        threading.Thread(target=self._pack_worker, args=(Path(apk), target, name, version, password, Path(key_path), key_password), daemon=True).start()

    def _pack_worker(self, apk, target, name, version, password, key_path, key_password):
        try:
            fingerprint, digest = encrypt_apk(apk, target, name, version, password, key_path, key_password)
            self.after(0, lambda: self._pack_done(target, fingerprint, digest))
        except Exception as exc:
            self.after(0, lambda error=str(exc): self._pack_error(error))

    def _pack_done(self, target, fingerprint, digest):
        self.status.set("Pacote .jxa criado e assinatura local verificada.")
        messagebox.showinfo("Sucesso", f"Pacote criado:\n{target}\n\nFingerprint do publicador:\n{fingerprint}\nSHA-256 do pacote:\n{digest}\n\nNo JeaxLabs OS 1.4.1, abra Configurações > Pacotes .jxa, cadastre este fingerprint por um canal independente e use a senha de criptografia informada ao empacotar.", parent=self)

    def _pack_error(self, error):
        self.status.set("Falha no empacotamento.")
        messagebox.showerror("Não foi possível criar o pacote", error, parent=self)


if __name__ == "__main__":
    AppPacker().mainloop()
