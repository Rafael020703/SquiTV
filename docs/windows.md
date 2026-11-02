# Windows — início do aplicativo

## Escopo atual

Windows x64: login em servidor Xtream, validação de conta ativa/validade, informações da conta e fuso do servidor, categorias e listagem de canais ao vivo, busca na categoria e nova tentativa após erro.

Os canais são consultados apenas ao selecionar uma categoria. Não há consulta global automática nem player Windows nesta etapa. Filmes, séries, favoritos, sincronização e instalador de atualizações Windows ainda não foram portados.

O núcleo `:shared` fornece modelos/parser Xtream e contrato de distribuição. UI Compose Desktop, HTTP/OkHttp cancelável e DPAPI ficam em `:desktopApp`. Nenhum módulo Windows depende de `:app`.

## Build

JDK 21 completo, com `bin/jpackage.exe`, e Windows x64. Definir `JAVA_HOME` para esse JDK na sessão. Não é necessário mudar o Java do Android Studio. O plugin Compose obtém suas ferramentas de empacotamento.

```powershell
# Release Windows (padrão)
.\gradlew.bat :shared:test :desktopApp:test :desktopApp:createDistributable :desktopApp:packageExe --console=plain

# Debug Windows, instalável ao lado da release
.\gradlew.bat :desktopApp:test :desktopApp:createDistributable :desktopApp:packageExe '-PdesktopVariant=debug' --console=plain
```

| Variante | Instalador | Diretório de build | Dados locais |
| --- | --- | --- | --- |
| Release | SquiTV-1.21.0.exe | desktopApp/build/release/compose/binaries/main/exe | %LOCALAPPDATA%/SquiTV |
| Debug | SquiTVDebug-1.21.0.exe | desktopApp/build/debug/compose/binaries/main/exe | %LOCALAPPDATA%/SquiTVDebug |

Cada variante tem nome no menu Iniciar e UUID de upgrade próprios. Preservar os UUIDs no Gradle em atualizações futuras. `packageExe` é o instalador da variante escolhida por `desktopVariant`; os nomes de tarefas de minificação do plugin não definem a identidade debug/release do aplicativo.

Os instaladores incluem Java e bibliotecas; o usuário final executa apenas o instalador `.exe`. A pasta `main/app` contém a imagem sem instalador, útil para desenvolvimento. Os executáveis iniciais não têm assinatura Authenticode.

## Conta e rede

- “Lembrar minha conta” é opcional. DPAPI vincula o arquivo `account.dpapi` ao usuário Windows; não gravar credenciais em JSON aberto, logs ou argumentos de processo.
- A conta lembrada preenche o formulário. Conexão requer ação do usuário.
- “Sair da conta” encerra a sessão; a conta lembrada continua disponível. Entrar desmarcando “Lembrar” remove a cópia salva da variante atual.
- Requisições respeitam cancelamento e limites de tempo/tamanho. Trocar categoria cancela a consulta anterior. Redirects não encaminham credenciais a outro endereço; usar o endereço final do provedor.
- HTTP é aceito para provedores que o exigem; o cliente de atualização Android mantém política HTTPS independente.

## Verificação e limites

Testes de parser/conta, transporte HTTP controlado, cancelamento, armazenamento DPAPI e renderização de telas são automatizados. Os previews usam apenas dados fictícios e ficam em `desktopApp/build/preview`.

O argumento técnico `--self-check <arquivo-de-saída>` verifica o runtime empacotado, o contrato compartilhado e um ciclo DPAPI em diretório temporário. Não acessa a conta real nem se conecta ao provedor.

Não declarar reprodução de vídeo ou compatibilidade com todos os provedores comprovadas a partir desses testes. A versão inicial não possui player.

## Próxima distribuição

A release Android v1.21 não contém instalador Windows. Não alterar seu manifesto após a publicação. Uma futura release conjunta deverá declarar os artefatos Windows realmente compilados no mesmo contrato schema v1, com nome, tamanho e SHA-256 exatos, preservando o APK exigido pelos clientes Android legados.
