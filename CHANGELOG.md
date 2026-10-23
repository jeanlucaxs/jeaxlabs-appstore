# Changelog — JeaxLabs OS

Notas de versões numeradas. As validações físicas precisam ser executadas no tablet antes de declarar a versão totalmente testada.

## [1.6.0] - 2026-10-23

- Tela de abertura preta, com logo centralizada por aproximadamente 15 segundos e sem indicador de contagem.
- Assistente inicial exige Wi-Fi com acesso à internet antes de concluir a configuração.
- Barra de navegação própria com recentes, início e voltar, com transições.
- Lista de aplicativos limitada àqueles instalados por pacotes JXA autorizados.
- Loja lê uma pasta por aplicativo, com app.json, pacote JXA e até 25 PNGs opcionais.
- Loja exibe título, descrição, versão, desenvolvedor e categoria, e não mostra placeholders para imagens ausentes.
- Instalação JXA não pede senha por aplicativo; o publicador é conferido por um certificado interno.
- Ferramentas Python de empacotamento removidas da árvore atual e dos artefatos públicos de build.
- CI e teste no tablet físico ainda precisam ser verificados para esta versão.

## [1.5.0] - 2026-10-22

- Interface atualizada com barra de navegação própria e transições animadas.
- Atalhos da tela inicial para Loja JeaxLabs, aplicativos, Wi-Fi e configurações.
- Catálogo configurado para ler pacotes .jxa no repositório público.
- Busca de redes Wi-Fi próximas; consulta de atualizações bloqueada até existir Wi-Fi com internet validada.
- Tela de manutenção e navegação para aplicativos recentes.

## [1.4.1] - 2026-10-22

- Integrado ao APK o importador de pacotes .jxa por HTTPS, com validação de fingerprint Ed25519, assinatura, SHA-256 e AES-GCM.
- Integrado o formato .jxu para atualização do próprio JeaxLabs OS.
- Atualizações verificadas são encaminhadas ao instalador Android; o sistema pode exigir confirmação.

## [1.4.0] - 2026-10-22

- Consolidada a experiência de launcher/kiosk em uma única Activity.
- Incluídos fluxos internos de configuração, diagnóstico e Task Locker.
