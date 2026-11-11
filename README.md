# Squi TV

Aplicativo para Android e Windows para assistir a canais ao vivo, filmes e séries de servidores compatíveis com Xtream Codes. Tema escuro, com navegação por controle remoto/toque no Android e mouse/teclado no Windows. A versão Windows está em desenvolvimento e ainda não tem todas as funções Android.

[Android](https://github.com/Rafael020703/SquiTV/releases/latest) · [Windows 1.27.0](https://github.com/Rafael020703/SquiTV/releases/tag/v1.27) · [Histórico de versões](https://github.com/Rafael020703/SquiTV/releases)

Este README descreve o código do projeto. Recursos em desenvolvimento podem ainda não estar disponíveis no APK da última release.

## Recursos

PiP, segundo plano e transferência pela rede local desta lista são funções Android. O Windows também permite baixar filmes e episódios diretos, inclusive por temporada, com uma transferência por vez. No Windows, consulte a seção própria abaixo para o que já foi portado e seus limites.

- **Ao vivo:** agrupamento de versões SD/HD/Full HD/4K, escolha de qualidade e troca circular dentro da lista de origem, incluindo favoritos. A disponibilidade das versões depende do provedor.
- **Programação no player:** programa atual e seguintes durante a reprodução de canais, quando fornecidos pelo servidor.
- **Filmes e séries:** detalhes, temporadas, episódios, favoritos, histórico e continuação da reprodução.
- **Categorias:** modo do servidor ou filtros por gênero; títulos sem gênero utilizam a categoria do servidor como fallback de apresentação.
- **Organização:** categorias ocultas, ordem personalizada e categorias fixadas.
- **Player:** Media3/ExoPlayer no Android e LibVLC no Windows; áudio, legendas, Ajustar/Preencher/Zoom, velocidade e temporizador, conforme o conteúdo disponível.
- **Multi-view:** grade 2×2, seleção por categorias e busca, respeitando agrupamento e qualidade dos canais; a quantidade de streams simultâneos depende do provedor e aparelho.
- **PiP e segundo plano:** controles independentes; Picture-in-Picture depende do suporte do aparelho.
- **Downloads:** gerenciamento de filmes e episódios para reprodução local, conforme suporte do conteúdo.
- **Backup e transferência:** exportação/importação seletiva, incluindo favoritos e organização, e transferência pela rede local entre versões compatíveis.
- **Login pela rede local:** preenchimento das credenciais da TV pelo navegador de outro aparelho da mesma rede.
- **DNS do aplicativo:** Google, Cloudflare, seleção automática por tempo de resposta e opção de desativar, acessíveis pelo login e pela dashboard. A escolha não altera o DNS de outros aplicativos.
- **Atualizações:** consulta ao GitHub, download e abertura do instalador após ação do usuário. A instalação depende das permissões e da confirmação do Android.

O aplicativo não fornece canais, filmes, séries ou credenciais. É necessário configurar um servidor e uma conta compatíveis.

## Instalação no Android

1. Abra a [última release](https://github.com/Rafael020703/SquiTV/releases/latest).
2. Baixe o APK `SquiTV-v<versão>.apk` nos assets da publicação.
3. Transfira o arquivo para o Android e abra-o para instalar. Autorize a instalação para o aplicativo que abriu o arquivo quando solicitado.
4. Configure os dados do servidor e da conta.

Os APKs publicados incluem ARM64 e ARMv7 e exigem Android 7.0/API 24 ou superior. Compatibilidade de codecs e PiP varia conforme o aparelho. Quando disponibilizado, o arquivo `.apk.sha256` permite conferir a integridade do download.

Para atualizar, use um APK do mesmo pacote e com assinatura compatível. A versão debug tem pacote próprio e pode coexistir com a release.

## Instalação no Windows

1. Abra a [release Windows 1.27.0](https://github.com/Rafael020703/SquiTV/releases/tag/v1.27) e baixe `SquiTV-1.27.0.exe` para Windows x64.
2. Execute o instalador e siga as etapas. A partir do Windows 1.22.1, o EXE verifica as bibliotecas de reprodução e baixa/prepara automaticamente o VLC oficial somente quando necessário. Java está incluído; não é preciso instalar dependências manualmente. A primeira preparação sem bibliotecas válidas requer internet.
3. Instalações novas usam `C:\Program Files\Squi TV`, com permissão de administrador e opção de outra pasta. Atualizações preservam a pasta e o contexto da instalação existente, inclusive as antigas por usuário; conta e configurações são mantidas. Debug possui identidade/dados separados.
4. A desinstalação normal remove arquivos, atalhos, dados e componentes em cache do aplicativo. Essa limpeza não roda durante upgrades. Instaladores baixados manualmente e registros de manutenção do próprio Windows ficam sob controle do usuário/sistema.

A distribuição atual usa instalador sem assinatura Authenticode; os checksums SHA-256 acompanham os arquivos. Consulte [Windows](docs/windows.md) para os recursos implementados e os limites.

## Login na TV pelo navegador

1. Na tela de login da TV, abra a opção de login/pareamento pela rede local.
2. No celular ou computador da mesma rede, abra o endereço ou QR exibido pela TV. Use o endereço completo informado.
3. Preencha os dados do servidor, usuário e senha e aguarde a confirmação.

O endereço tem autorização temporária e não deve ser compartilhado. Esse login usa HTTP na rede do usuário. A rede precisa permitir comunicação entre os aparelhos.

## Organização e preparação do catálogo

Filmes e séries permitem escolher categorias do provedor ou gêneros informados nos metadados. Categorias do servidor utilizadas como fallback não são gravadas como gêneros reais.

O painel de preparação mostra percentual e estimativa de tempo restante, com pausa, retomada, novas tentativas e velocidades Lento/Normal/Rápido. São até 1/2/5 consultas simultâneas, respectivamente; falhas reduzem automaticamente o ritmo. A estimativa usa o processamento recente e fica indisponível durante pausas ou falhas pendentes. O tempo total e a disponibilidade de gêneros dependem do provedor. A preparação é distinta da sincronização inicial das listas.

## Backup e transferência local

Nas configurações, selecione os dados a exportar/importar. Favoritos, categorias ocultas e ordem personalizada podem fazer parte do arquivo.

Para transferir entre aparelhos, abra o recebimento no destino e o envio no outro dispositivo, na mesma rede. Confira o destino e as opções na interface. Formatos novos podem não ser aceitos por versões antigas; confira a compatibilidade entre os aparelhos.

O backup de configurações não inclui os arquivos de vídeo baixados.

No Windows, o backup permite selecionar grupos e conferir uma prévia antes de importar. Favoritos são combinados com os existentes; grupos desmarcados são mantidos. Backups Windows antigos completos continuam aceitos, mas os novos arquivos seletivos exigem um leitor compatível. Os formatos Android e Windows ainda não são intercambiáveis.

## Desenvolvimento

### Ambiente

- Android Studio compatível com o Android Gradle Plugin declarado em `gradle/libs.versions.toml`.
- Android SDK da versão de `compileSdk` em `app/build.gradle.kts` (atualmente API 37) e Build Tools 36.0.0.
- Java 21 para o daemon Gradle, conforme `gradle/gradle-daemon-jvm.properties`. Configure um JDK compatível no Android Studio; o launcher pode ser diferente do daemon.
- Use o Gradle Wrapper incluído no projeto; não é necessário instalar Gradle global.

Clone o repositório e abra a pasta raiz no Android Studio:

```powershell
git clone https://github.com/Rafael020703/SquiTV.git
```

O Android Studio pode criar `local.properties` com o caminho local do SDK. Esse arquivo não deve ser commitado.

### Compilação e verificações

Na raiz do projeto, no PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:lintDebug --console=plain
.\gradlew.bat :app:assembleDebugAndroidTest --console=plain
```

No Linux/macOS, use `./gradlew` no lugar de `.\gradlew.bat`.

| Variante | Pacote | Uso |
| --- | --- | --- |
| Debug | `rsv.squitv.debug` | Desenvolvimento; instala ao lado da release |
| Release | `rsv.squitv` | Distribuição assinada, com R8 e redução de recursos |

O APK debug é gerado em `app/build/outputs/apk/debug/`. `assembleDebugAndroidTest` somente compila o APK instrumentado; executar esses testes exige um emulador ou dispositivo configurado. Testes unitários e compilação não comprovam foco, controle remoto ou PiP em hardware físico.

### Assinatura e release

O build lê as seguintes propriedades locais de `local.properties`:

```properties
signing.release.storeFile=<caminho-local-do-keystore>
signing.release.storePassword=<senha-local>
signing.release.keyAlias=<alias>
signing.release.keyPassword=<senha-local-da-chave>
```

Configure-as apenas no seu ambiente. Não coloque valores reais, keystores ou tokens no repositório ou em relatórios.

```powershell
.\gradlew.bat :app:assembleRelease :app:lintRelease --console=plain
```

O APK é gerado em `app/build/outputs/apk/release/`. Antes de publicar, confira versão/code, migrações, assinatura compatível com a distribuição anterior, metadados do APK e SHA-256. APKs e checksums devem ser assets de uma GitHub Release.

### Contrato de Atualizações e Geração de Manifesto

O projeto utiliza o módulo compartilhado `:shared` (Kotlin/JVM puro) para validação de versão e seleção de artefatos de atualização. O manifesto de distribuição (`squitv-update.json`) segue o Schema v1.

O módulo Windows inicial reutiliza `:shared`; sua interface e armazenamento são próprios. A base de atualização separa os artefatos por plataforma e mantém compatibilidade com releases Android sem manifesto. Consulte [o contrato](docs/contrato-atualizacoes.md), [a arquitetura](docs/arquitetura-plataformas.md) e [o checklist de release](docs/checklist-release.md). O gerador lê metadados do APK assinado e produz o staging local; não publica automaticamente.

Para gerar e validar o manifesto a partir de um APK compilado:
```powershell
python -B tools/generate_manifest.py app/build/outputs/apk/release/app-release.apk --tag v1.21 --output-dir .artifacts/release-v1.21
```

O `.gitignore` exclui builds, caches, arquivos locais, material privado de assinatura e planos/relatórios locais do Gemini. Wrapper do Gradle, schemas do Room, recursos e notas de versão continuam versionados. Adicionar um padrão ao `.gitignore` não remove arquivos já rastreados nem apaga o histórico.

## Windows — desenvolvimento inicial

`:desktopApp` contém login Xtream, dashboard, categorias/busca e player ao vivo com LibVLC, EPG e troca circular na lista de origem. Filmes/Séries têm detalhes, temporadas, episódios, favoritos e histórico/retomada. Catálogos oferecem categorias Servidor/Filtros por gênero com preparação econômica, contagens e ocultação das vazias. Favoritos da dashboard e Multi-view têm destinos funcionais; Continuar assistindo fica nos catálogos/detalhes. Configurações incluem conta, DNS, reprodução, agrupamento/qualidade ao vivo, organização/ocultação por conta e Atualizações. O backup de preferências inclui favoritos, modos e organização; histórico/cache de preparação ficam em arquivos próprios e não entram nesse backup. Ainda não é intercambiável com Android. Players reutilizam seek, controles e apresentação Android em `:shared`/`uiCommon`; cada plataforma mantém seu motor e armazenamento. A atualização release Windows oferece verificação, download e instalação explícita. Na debug, Atualizações permanece visível com produção desabilitada. Downloads de filmes e episódios diretos estão disponíveis, inclusive por temporada, com uma transferência por vez e reprodução local. Não há HLS/DASH, retomada parcial ou serviço após fechar. Perfis/sincronização e cache offline completo de favoritos continuam pendentes. Não há paridade completa nem comprovação local de reprodução/upgrade da release nesta etapa.

As variantes Windows release e debug têm nomes, identidades e armazenamento separados. A release usa instalador `.exe`; para desenvolver, a debug pode ser portátil com atalho no menu Iniciar (`tools/deploy-windows-debug.ps1`). A debug portátil inclui Java e LibVLC; a release inclui Java e seu instalador prepara as bibliotecas de reprodução quando necessário. A conta é salva opcionalmente com DPAPI do usuário do Windows, sem senha em texto no disco.

Consulte [Windows: build, instalação e limites](docs/windows.md). As atualizações Windows têm notas e artefatos próprios, sem alterar a release Android.

## Tecnologia

Kotlin, Coroutines/Flow, Jetpack Compose/Material 3, Media3/ExoPlayer, Hilt, Room, DataStore, Retrofit/OkHttp, Kotlinx Serialization e WorkManager. As versões efetivas estão no catálogo Gradle.

## Documentação

- [Notas de versões](docs/releases/)
- [Desenvolvimento](docs/desenvolvimento.md)
- [Arquitetura das plataformas](docs/arquitetura-plataformas.md)
- [Contrato de atualizações](docs/contrato-atualizacoes.md)
- [Checklist de release](docs/checklist-release.md)
- [Windows](docs/windows.md)

Relatórios datados documentam a etapa em que foram produzidos; não substituem a validação do checkout atual.

## Autoria

Desenvolvido por **rafasqui**. Todos os direitos reservados.

### Preparação de atualizações por plataforma

A versão v1.22 reúne Android 1.22/code 23 e Windows 1.22.0. Notas por plataforma: [Android](docs/releases/v1.22-android.md) e [Windows](docs/releases/v1.22-windows.md).

A versão conjunta [v1.24](https://github.com/Rafael020703/SquiTV/releases/tag/v1.24) reúne Android 1.24/code 25 e Windows 1.24.0. Inclui ajustes de reprodução/histórico, volume por setas, memória dos catálogos e capas. Windows recebe downloads de episódios/temporadas, navegação por teclado/mouse e ajustes de janela. Notas separadas: [Android](docs/releases/v1.24-android.md) e [Windows](docs/releases/v1.24-windows.md). Mantém o instalador online e preserva os assets das versões anteriores.

O Android recebeu a atualização exclusiva [v1.24.1](https://github.com/Rafael020703/SquiTV/releases/tag/v1.24.1), código 26, com compatibilidade da leitura de assinatura e erros persistentes/navegáveis na tela de atualização. Naquela publicação, Windows permaneceu 1.24.0 na release conjunta v1.24. [Notas Android](docs/releases/v1.24.1-android.md). No aparelho bloqueado pelo atualizador anterior, abra o APK oficial v1.24.1 manualmente sobre a instalação existente.

A publicação exclusiva Android [v1.24.2](https://github.com/Rafael020703/SquiTV/releases/tag/v1.24.2), código 27, manteve o mesmo código funcional da v1.24.1. Ela permite conferir a atualização pelo app a partir da v1.24.1 instalada manualmente: Configurações → Atualizações → Verificar atualizações. [Notas Android](docs/releases/v1.24.2-android.md). Naquela publicação, Windows permaneceu 1.24.0, com sua referência estável preservada.

A publicação conjunta [v1.25](https://github.com/Rafael020703/SquiTV/releases/tag/v1.25) reuniu Android **1.25/code 28** e Windows **1.25.0**. Incluiu preparação em lotes com progresso/estimativa e correção do cancelamento do próximo episódio. Windows também recebeu categorias recentes, ordenação, contas salvas e melhorias de detalhes, backup e atualização. Notas: [Android](docs/releases/v1.25-android.md) e [Windows](docs/releases/v1.25-windows.md).

A release conjunta atual é a [v1.26](https://github.com/Rafael020703/SquiTV/releases/tag/v1.26), com Android **1.26/code 29** e Windows **1.26.0**. Inclui DNS somente no aplicativo e ajustes na sincronização ao entrar na conta; o Windows também recebe backup seletivo com prévia de importação. Notas: [Android](docs/releases/v1.26-android.md) e [Windows](docs/releases/v1.26-windows.md). Atualizações usam o manifesto conjunto e notas próprias de cada plataforma.

A atualização exclusiva [Windows **1.26.1**](https://github.com/Rafael020703/SquiTV/releases/tag/v1.26.1.1) inclui ajustes de janela, detalhes dos conteúdos e apresentação do instalador. [Notas Windows](docs/releases/v1.26.1-windows.md). O Android permanece na v1.26/code 29, e a publicação conjunta v1.26 continua como Latest para compatibilidade com os clientes Android.

A atualização exclusiva [Windows **1.26.2**](https://github.com/Rafael020703/SquiTV/releases/tag/v1.26.2.1) adiciona uma reconexão automática para canais ao vivo e o diagnóstico local em Notificações. [Notas Windows](docs/releases/v1.26.2-windows.md).

A atualização exclusiva [Windows **1.26.3**](https://github.com/Rafael020703/SquiTV/releases/tag/v1.26.3.1) corrige a fila de downloads e a reprodução offline de filmes e séries. [Notas Windows](docs/releases/v1.26.3-windows.md).

A [v1.27 para Android e Windows](https://github.com/Rafael020703/SquiTV/releases/tag/v1.27) prioriza o novo servidor principal e tenta o próximo se um canal ao vivo não iniciar em cinco segundos. [Notas Android](docs/releases/v1.27-android.md) · [Notas Windows](docs/releases/v1.27-windows.md).

`tools/prepare_release.py` prepara APK/EXE, checksums, manifesto com changelogs separados, plano de release e candidatos de referência por plataforma. Não publica. Correções exclusivas podem manter o mesmo número nos dois apps; as tags únicas e a proteção de Latest estão descritas no [contrato](docs/contrato-atualizacoes.md). Os arquivos de [referência](updates/README.md) só são avançados depois de uma publicação autorizada e conferida.
