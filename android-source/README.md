# JeaxLabs OS — documentação técnica

A versão e o código Android são definidos no arquivo raiz `version.properties`. O aparelho-alvo inicial é o Samsung Galaxy Tab A7 Lite LTE (SM-T225).

## Páginas internas

- Tela de boot e boas-vindas JeaxLabs.
- Tela de bloqueio JeaxLabs simulada; ao apagar/acender a tela, a Activity tenta mostrar o bloqueio de novo se ainda estiver viva.
- Tela inicial, gaveta e busca de aplicativos, fichas de apps simuladas e dock. Os ícones não iniciam APKs externos.
- Configurações internas: Wi-Fi e conexões, Bluetooth, brilho/suspensão/rotação, volume, bateria, notificações, apps, armazenamento, segurança, diagnóstico ADB e Task Locker.
- Ao reconhecer Device Owner, o app tenta ativar Lock Task automaticamente, ocultar barras e cortina de notificações, desabilitar keyguard quando permitido, bloquear a desinstalação do próprio app e se tornar launcher HOME persistente.
- Wi-Fi usa as APIs de gerenciamento de rede conforme o nível de privilégio; Bluetooth usa permissões runtime e requer Device Owner para controle direto de rádio em Android moderno.

## Provisionamento indispensável via PC

Device Owner não é uma permissão runtime; o APK não pode concedê-la a si próprio. O tablet normalmente precisa estar recém-restaurado e sem contas adicionadas. Com ADB autorizado pelo usuário, execute no PC:

    adb shell dpm set-device-owner br.com.jeaxlabs.os/.JeaxDeviceAdminReceiver
    adb shell dpm list-owners

Se um PIN/padrão seguro do Android já estiver configurado, a política setKeyguardDisabled pode ser recusada; o APK não remove credenciais existentes. A permissão Notification Access também é protegida pelo Android e exige confirmação especial fora da Activity. Esta versão não abre a tela de Configurações automaticamente; sem essa autorização, a central interna não lê notificações de outros apps. O Android ainda pode mostrar diálogos próprios de permissões/conexão protegida.

## Ferramentas de pacote

- O repositório da loja fornece `tools/JeaxLabsAppPacker.py`, que gera `.jxa` com AES-256-GCM e assinatura Ed25519.
- O repositório de atualizações fornece `tools/JeaxLabsUpdatePacker.py`, que gera `.jxu` com AES-256-GCM e assinatura Ed25519.
- Para executar as ferramentas, instale Python 3 e rode `py -m pip install cryptography`.
- Guarde a chave privada fora do GitHub público. O receptor deve confiar no fingerprint por um canal independente.
- O APK ainda não implementa o instalador/descriptografador dos formatos `.jxa`/`.jxu`.

## Versionamento e build oficial

A workflow de CI gera builds de verificação identificados como `CI`; não são lançamentos para distribuição. A workflow `Official Release` publica somente tags `vX.Y.Z` correspondentes à versão central, com APK assinada pela chave persistente de produção, teste automatizado, verificação de assinatura, SHA-256, fontes e os dois empacotadores Python. Ela também sincroniza `updates.json` no repositório `jeaxlabs-os-updates`.

É necessário configurar os GitHub Actions Secrets descritos em [RELEASE_POLICY.md](../RELEASE_POLICY.md) antes da primeira publicação assinada.

## Limites técnicos

Este APK continua sendo um launcher/simulador sobre Android, não uma ROM/firmware independente. Não substitui bootloader, recovery, animação nativa da Samsung nem serviços privilegiados. Task Lock, Wi-Fi/Bluetooth, keyguard e comportamento ao voltar do modo de suspensão precisam ser verificados no tablet real; não declarar teste físico concluído antes de executá-lo.
