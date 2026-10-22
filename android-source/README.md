 # JeaxLabs OS — ROM Simulator 1.3.0

Aplicativo Android que simula parte da experiência do futuro JeaxLabs OS sobre o Android instalado. Primeiro dispositivo-alvo de teste: Samsung Galaxy Tab A7 Lite LTE (SM-T225).

## Interface simulada

- Inicialização JeaxLabs, boas-vindas e configuração inicial.
- Tela de bloqueio simulada com relógio, data e resumo opcional de notificações.
- Tela inicial com fundo em degradê, widget de clima configurável, pesquisa, aplicativos reais detectados no aparelho, dock e atalhos.
- Gaveta de aplicativos com pesquisa e abertura de apps instalados.
- Central de controle JeaxLabs.

## Conectividade e recursos

- Tela Bluetooth própria do JeaxLabs OS: status do adaptador, lista de dispositivos pareados, busca de dispositivos próximos e solicitação de pareamento. O Android ainda pode pedir permissão de dispositivos próximos e confirmação do pareamento.
- Painéis de sistema flutuantes para Wi-Fi/internet e volume, quando suportados pelo Android. A interface do painel é fornecida pelo sistema, porém aparece sobre a aplicação, sem obrigar o usuário a navegar até a página completa de Configurações.
- Atalhos para brilho, tela, som, bateria, notificações e opções de desenvolvedor.
- Central de notificações opcional, após autorização explícita de Notification Access.
- JeaxLabs Task Locker com PIN e Lock Task Mode do Android quando o aplicativo estiver provisionado como Device Owner.
- Loja consulta o catálogo remoto; canal de atualizações consulta metadados.

## Limitações honestas

Este APK é uma **simulação de interface e navegação**, não uma ROM/firmware. Não substitui o bootloader, a animação original, a recovery, os serviços privilegiados da Samsung nem os componentes do sistema Android.

As permissões de Bluetooth são solicitadas ao usuário. No Android 13 ou superior, um app normal não pode ligar/desligar livremente o Bluetooth; a exceção inclui aplicativos Device Owner/Profile Owner e aplicativos de sistema. O privilégio de Device Owner precisa ser devidamente provisionado no dispositivo. Os painéis de Wi-Fi/volume são fornecidos pelo Android; um APK normal não pode ignorar restrições do sistema para alterar rádios livremente.

O widget permite guardar o nome da cidade, mas ainda não apresenta uma previsão meteorológica real. A loja não instala automaticamente pacotes criptografados .jxa, e o canal ainda não aplica pacotes .jxu. O Task Locker e as telas devem ser testados no hardware e versão Android reais antes de uso de produção.

## Build

GitHub Actions usa Java 17 e Gradle 8.9 e gera um APK debug e o ZIP completo do build. O APK de depuração é para testes e não é assinado com uma chave de distribuição de produção.

Com o ADB autorizado e o tablet preparado para provisionamento, o comando para definir Device Owner é:

~~~bat
adb shell dpm set-device-owner br.com.jeaxlabs.os/.JeaxDeviceAdminReceiver
adb shell dpm list-owners
~~~

O provisionamento normalmente exige restauração de fábrica e deve ser feito antes de adicionar contas ao dispositivo.
