# JeaxLabs OS — simulador de sistema 1.4.0

Objetivo: simular uma experiência fechada de sistema dentro de uma única Activity/APK enquanto o firmware/ROM verdadeiro é desenvolvido. Primeiro aparelho de teste: Samsung Galaxy Tab A7 Lite LTE (SM-T225).

## Páginas internas
- Tela de boot e boas-vindas JeaxLabs.
- Tela de bloqueio JeaxLabs com relógio, data e desbloqueio simulado; ao apagar/acender a tela, a Activity tenta mostrar o bloqueio de novo se ainda estiver viva.
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
- Repositório da loja: tools/JeaxLabsAppPacker.py gera .jxa com AES-256-GCM e assinatura Ed25519.
- Repositório de atualizações: tools/JeaxLabsUpdatePacker.py gera .jxu com AES-256-GCM e assinatura Ed25519.
- Para rodar as ferramentas: Python 3 e `py -m pip install cryptography`.
- Guarde a chave privada fora do GitHub público. O receptor tem que confiar no fingerprint por um canal independente.
- O APK ainda não implementa o instalador/descriptografador dos formatos .jxa/.jxu.

## Limites técnicos
Este APK continua sendo um simulador sobre Android, não uma ROM/firmware independente. Não substitui bootloader, recovery, a animação nativa da Samsung nem serviços privilegiados. Task Lock, Wi-Fi/Bluetooth, keyguard e comportamento ao voltar do modo de suspensão precisam ser verificados no tablet real.

## Build
GitHub Actions gera APK debug e ZIP com código-fonte. APK debug serve para testes, não é assinatura de produção.
