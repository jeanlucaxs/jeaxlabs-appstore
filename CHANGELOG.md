# Changelog — JeaxLabs OS

Notas de versões numeradas e publicadas. Descrever apenas recursos incluídos e verificações realmente feitas.

## [1.4.1] - 2026-10-22

- Integrado ao APK o importador de pacotes `.jxa` por HTTPS, com validação de fingerprint Ed25519, assinatura, SHA-256 e senha AES-256-GCM.
- Integrado o formato `.jxu` para atualização do próprio JeaxLabs OS: o pacote contém o novo APK cifrado, a versão, o versionCode, a versão mínima exigida e as notas da atualização.
- A atualização só é encaminhada ao PackageInstaller depois da verificação criptográfica e da confirmação de que o APK é o pacote `br.com.jeaxlabs.os`, com a versão e o versionCode declarados.
- O Android pode exigir confirmação; a substituição só funciona se o APK novo tiver o mesmo certificado de assinatura da instalação atual e um versionCode maior.
- O workflow de release foi preparado para publicar o APK e seu `.jxu`, além de sincronizar o manifesto de atualizações. A compilação final e a instalação no tablet físico ainda precisam ser confirmadas.


## [1.4.0] - 2026-10-22

- Consolidada a experiência de launcher/kiosk em uma única Activity, com telas internas de início, bloqueio simulado e configurações.
- Incluídos fluxos internos de configuração e diagnóstico, com políticas avançadas condicionadas ao provisionamento Device Owner.
- Empacotadores Python da loja (.jxa) e de atualizações (.jxu) incluídos nos artefatos de código-fonte.
- Limite conhecido: o pacote .jxa/.jxu ainda não é instalado ou atualizado pelo APK, e o comportamento kiosk precisa de validação no tablet físico.

