# Changelog — JeaxLabs OS

Notas de versões numeradas e publicadas. Descrever apenas recursos incluídos e verificações realmente feitas.

## [1.4.1] - 2026-10-22

- Integrado ao APK o importador de pacotes `.jxa` por URL HTTPS.
- Adicionada verificação Ed25519 do manifesto e do fingerprint confiável, validação SHA-256 do conteúdo e descriptografia AES-256-GCM com PBKDF2-HMAC-SHA256 compatível com o App Packer.
- Após a validação, o APK descriptografado é enviado ao PackageInstaller do Android; a instalação silenciosa só é tentada quando o modo Device Owner permite, e o sistema pode exigir confirmação.
- O funcionamento no tablet físico e a instalação real ainda precisam ser validados em dispositivo.


## [1.4.0] - 2026-10-22

- Consolidada a experiência de launcher/kiosk em uma única Activity, com telas internas de início, bloqueio simulado e configurações.
- Incluídos fluxos internos de configuração e diagnóstico, com políticas avançadas condicionadas ao provisionamento Device Owner.
- Empacotadores Python da loja (.jxa) e de atualizações (.jxu) incluídos nos artefatos de código-fonte.
- Limite conhecido: o pacote .jxa/.jxu ainda não é instalado ou atualizado pelo APK, e o comportamento kiosk precisa de validação no tablet físico.

