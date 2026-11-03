# Squi TV para Windows

## Estado e plataformas

A versão conjunta [**v1.23**, publicada em 03/11/2026](https://github.com/Rafael020703/SquiTV/releases/tag/v1.23), reúne Android **1.23/code 24** e Windows **1.23.0 x64**. Notas: [Android](releases/v1.23-android.md) e [Windows](releases/v1.23-windows.md). É a versão Latest para os clientes Android legados também. Publicações anteriores continuam no histórico; versões e assets antigos não foram substituídos.

O Windows aproxima funções e apresentação do Android, mas ainda não tem paridade completa. `:desktopApp` consome Kotlin puro em `:shared` e fontes Compose em `uiCommon`; nunca depende de `:app`. Motores, armazenamento e integrações de sistema permanecem específicos de cada plataforma.

O aplicativo não fornece conteúdo ou credenciais. É necessário um servidor compatível com Xtream Codes. Disponibilidade de metadados, EPG, qualidades e transmissões simultâneas depende do provedor e do aparelho.

## Funções implementadas

| Área | Comportamento atual | Limites |
| --- | --- | --- |
| Login/DNS | Seleção/teste de DNS, conta lembrada opcional e reconexão única | Senhas locais protegidas por DPAPI; logout exige nova entrada manual |
| Dashboard | Ao vivo, Filmes, Séries, Buscar, Favoritos, Multi-view e Configurações | Downloads continua visual; Notificações também não possui ação no Android de referência |
| Ao vivo | Categorias, pesquisa local, agrupamento de versões e qualidade preferida | Troca circular exclusivamente no snapshot da lista de origem, inclusive favoritos/busca |
| Filmes/Séries | Detalhes, temporadas, episódios, favoritos e reprodução | Dados dependem do servidor; episódios não fazem loop ao terminar a série |
| Histórico | Progresso e retomada de filme/episódio por conta | Continuar assistindo fica nos catálogos/detalhes, nunca na dashboard |
| Categorias | Ordem/ocultação por conta/tipo, contagens e remoção das vazias confirmadas | Falha de índice mantém contagens desconhecidas; Recentes e algumas ordenações Android ainda faltam |
| Modos VOD | Servidor ou Filtros; gêneros A–Z primeiro, fallback servidor A–Z depois | Preparação de metadados não é instantânea nem baixa vídeo |
| Favoritos | Marcação nos cartões/player, sidebars e biblioteca com busca/filtros por tipo | Metadados ainda dependem do servidor quando não estão em memória |
| Multi-view | Grade 2×2, categorias antes dos canais, busca, agrupamento, áudio de uma tela e reconexão individual | Quatro decodificadores/streams simultâneos não foram observados nesta etapa |
| Player | Áudio, legendas, velocidade, temporizador, Ajustar/Preencher/Zoom e seek VOD compartilhado | Capacidades variam com o stream; EPG somente no player ao vivo comum |
| Configurações | Conta, DNS, reprodução, categorias, backup de preferências e Atualizações | Backup Windows não é intercambiável com Android; histórico/cache não entram nele |

A busca global consulta somente listagens dos tipos escolhidos, com cancelamento, erros parciais e índice de sessão. Não busca episódios individuais nem consulta detalhes/EPG para indexar. Exibe até 300 resultados e pede refinamento quando necessário.

Ao vivo/Multi-view carregam um índice de sessão ao entrar explicitamente. Filmes/Séries usam seu índice completo para contar/projetar categorias nos dois modos. Não há varredura global no login/dashboard nem uma requisição por categoria para calcular contagens. A consulta por categoria permanece como recuperação quando o índice falha. Catálogos grandes continuam sujeitos à latência e aos limites do transporte.

A preparação VOD mantém cache por conta/tipo, uma consulta de metadados por vez, lotes, pausas, cache negativo e tentativas limitadas. Pausa fora do catálogo ativo, em reprodução/segundo plano ou por solicitação. Velocidade/pausa do coordenador são de sessão. Não confundir preparação com sincronização das listas.

## Player e janela

- LibVLC usa superfície nativa; os HUDs mantêm SwingGraphics. No Multi-view Windows o cabeçalho do slot ocupa faixa acima do Canvas para não ficar sob o HWND; ainda difere do overlay Android.
- Ajustar conserva a imagem inteira e a proporção; Preencher pode esticar; Zoom preenche cortando bordas sem esticar. O adaptador limpa operações anteriores e recalcula conforme o viewport. Barras codificadas dentro do vídeo não são detectadas automaticamente.
- VOD compartilha controlador de seek Android: coalescimento de 300 ms, confirmação nativa e proteção por sourceId/generation contra eventos antigos. Pausa fica centralizada. Retomada depende de duração/seekability válidas.
- Movimento contínuo do mouse por 0,5 s revela controles; clique/teclado atuam imediatamente. Esc fecha primeiro um menu aberto; sem menu, retorna da reprodução mesmo com controles visíveis. Nunca altera fullscreen.
- Janela sem barra nativa ou controles por hover. Modo janela 16:9, inicial 1120×630 e mínimo proporcional. Primeira abertura em fullscreen; último modo salvo por variante. F11 alterna e fullscreen bloqueia redimensionamento/arraste.
- Na raiz, Esc/Voltar abre Sair/Ficar, com Ficar focado. Navegação de Voltar ocorre em KeyUp. Sem botões visuais Voltar/Home; sair da conta fica somente nas Configurações.
- Uma instância por variante/sessão Windows, inclusive entre builds portáteis. Debug e release podem coexistir.

## Compilação

Use Windows x64 e JDK 21 completo com `bin/jpackage.exe`. Configure `JAVA_HOME` na sessão; não é necessário mudar o Java do Android Studio.

```powershell
# Desenvolvimento: imagem debug autocontida e atalho no Iniciar
.\gradlew.bat :desktopApp:createDistributable '-PdesktopVariant=debug' --console=plain
.\tools\deploy-windows-debug.ps1

# Produção: empacotar sem executar o instalador
.\gradlew.bat :desktopApp:writeUpdateMetadata '-PdesktopVariant=release' --console=plain
```

`writeUpdateMetadata` gera o EXE online e registra versão, arquitetura, tamanho e SHA-256 reais. Não distribua o MSI intermediário ou a imagem portable como atualização release. Antes de alternar variante, preserve instaladores/metadados concluídos fora das saídas Gradle.

| Variante | Nome | Saída | Dados |
| --- | --- | --- | --- |
| Release | Squi TV | desktopApp/build/release/compose/binaries/main/exe/SquiTV-1.23.0.exe | %LOCALAPPDATA%/SquiTV |
| Debug | Squi TV Debug | desktopApp/build/debug/compose/binaries/main/app/SquiTVDebug | %LOCALAPPDATA%/SquiTVDebug |

UUIDs de upgrade e diretórios de dados são distintos e devem ser preservados. O nome de uma tarefa de minificação não define a identidade debug/release.

### Debug portátil

O deploy copia a imagem completa para `%LOCALAPPDATA%/SquiTVDebugPortable/builds/<hash>` e atualiza o atalho **Squi TV Debug**. Mantém builds anteriores, ícone em caminho físico, conta e processos abertos. Não inicia/encerra o app. Uma janela já aberta continua usando seu build anterior.

A imagem debug contém Java e VLC; mova a pasta inteira, não apenas o EXE. Dados continuam vinculados ao usuário Windows. Preenchimento de login debug é local, protegido e externo à distribuição; não embutir credenciais em código, JAR ou instalador. A release ignora os defaults e propriedades específicos da debug.

## Instalação e atualizações

- O usuário executa apenas `SquiTV-<versão>.exe`. Java está incluído. O instalador online valida/reutiliza o VLC fixado por hash e baixa/prepara automaticamente somente quando necessário; não carrega VLC arbitrário do sistema.
- Nova instalação por máquina: `C:\Program Files\Squi TV`, com UAC e escolha de pasta. Upgrade preserva destino/contexto existente, inclusive legado por usuário.
- Desinstalação normal remove dados/componentes do aplicativo. Limpeza não ocorre em upgrade: manter a proteção `REMOVE=ALL AND NOT UPGRADINGPRODUCTCODE`. Downloads manuais e registros de manutenção do Windows ficam sob controle do usuário/sistema.
- EXE Windows ainda não possui assinatura Authenticode. SHA-256 acompanha o instalador; o APK Android mantém sua assinatura de distribuição.
- Runtime, licenças e DLLs baixadas não entram no Git. O empacotamento inclui somente vetores Material Extended realmente usados, preservando os bytes originais e a licença.
- Atualizações é visível na debug, com rede/download/instalação de produção bloqueados, inclusive `force=true`. Release usa HTTPS dedicado, hash/tamanho/associação exata e instalação somente por clique explícito. Aviso da dashboard dura 10 segundos em foco.
- Notas são separadas por plataforma. Conjunta usa nova versão menor/tag numérica; Windows exclusivo usa tag técnica com quarta componente e nunca assume Latest. Referências estáveis só avançam após publicação e conferência dos assets. Ver [contrato](contrato-atualizacoes.md), [checklist](checklist-release.md) e [referências](../updates/README.md).

## Dados e manutenção

Preferências, favoritos e organização têm escopo por conta/tipo. Histórico e cache de preparação usam arquivos próprios validados, limites de tamanho e escrita atômica. Arquivo inválido não autoriza apagar dados ou atribuir IDs legados a uma conta por suposição. Perfis/sincronização Android e cache offline completo de favoritos continuam pendentes.

O `.gitignore` conserva planos, auditorias, registros de manutenção, dados privados, caches e builds localmente. Documentação pública permanente e notas de versões são versionadas; binários/checksums/manifesto são assets da release. Não publicar diretórios inteiros de staging nem relatórios contendo caminhos/dados pessoais.

## Evidências e limites

A preparação da v1.23 concluiu compilações, empacotamento, lint (zero erros, com avisos), assinatura Android compatível e conferência dos arquivos/manifesto. O [CI da revisão distribuída](https://github.com/Rafael020703/SquiTV/actions/runs/41642997574) passou nos jobs Android e Windows. Os cinco assets públicos foram baixados e tiveram tamanho/SHA-256 conferidos; APK de 7.283.921 bytes e instalador Windows de 71.510.528 bytes (68,20 MiB). As referências estáveis das duas plataformas apontam o mesmo manifesto da v1.23, com notas separadas.

Compilar APK de teste instrumentado não significa executá-lo. Não foram executados testes locais nesta rodada nem observados interface/reprodução/Multi-view ou upgrade da release instalada. Testes executados pelo CI remoto são evidência separada das verificações locais.

Execução, inspeção e verificações de instalação neste PC usam somente debug. A release instalada, seus processos e dados não podem ser abertos, encerrados, reinstalados ou alterados pelo agente. Sem ganhos de FPS/memória/abertura anunciados sem medição.

Histórico técnico detalhado e hashes de debug ficam nos registros locais ignorados; notas públicas de cada distribuição ficam em [releases](releases/).
