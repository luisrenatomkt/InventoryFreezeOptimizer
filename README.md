# Inventory Freeze FPS Optimizer — Minecraft Forge 1.8.9

Mod **somente de cliente**. Ao abrir o inventário (ou outro contêiner), o cenário 3D atrás da interface é
substituído por **uma única imagem capturada**, em vez de ser renderizado de novo a cada quadro. O HUD
(corações, fome, hotbar) e a interface continuam sendo desenhados ao vivo.

> ## ⚠️ Estado desta entrega — leia primeiro
> - **O JAR NÃO foi gerado.** O ambiente onde o projeto foi escrito bloqueou `maven.minecraftforge.net`,
>   `files.minecraftforge.net` e `repo1.maven.org` (403 de política de rede) e só tinha JDK 21. O Forge 1.8.9 não
>   compila sem baixar essas dependências e exige JDK 8.
> - **O código nunca foi compilado contra o Forge 1.8.9 nem executado no Minecraft.** A única verificação feita
>   foi um parse de sintaxe com `javac` (sem classpath do Minecraft): sem erros de sintaxe. Os nomes de
>   API (MCP `stable_22`) foram escritos de memória e **podem exigir pequenos ajustes** no primeiro build.
> - O `build.gradle` (Loom para 1.8.9) também **não foi executado**.
> - **Nenhum teste de jogo foi realizado.** Veja a seção "Relatório de testes".

## Como funciona

1. `RendererHook` troca o campo público `Minecraft#entityRenderer` por `FreezeEntityRenderer` (subclasse de
   `EntityRenderer`) na fase `postInit`. Não há coremod nem edição de bytecode.
   *Justificativa:* no 1.8.9 o único ponto em que dá para pular o mundo **sem pular também o HUD** é
   `EntityRenderer#renderWorld`, e o Forge não expõe evento cancelável para ele. (`Minecraft#skipRenderWorld`
   pula o HUD junto, então não serve.)
2. Em cada quadro, `renderWorld` decide:
   - **Tela elegível aberta e snapshot válido** → desenha um quad opaco em tela cheia com o snapshot e retorna
     (mundo, entidades, partículas, chunks, clima e mão **não** são renderizados).
   - **Tela elegível, sem snapshot ainda** (1º quadro, redimensionamento, troca de mundo) → renderiza o mundo
     normalmente **uma vez**, e logo depois (antes do HUD) copia o framebuffer para uma textura
     (`glCopyTexSubImage2D`). Como o HUD ainda não foi desenhado, o snapshot **não contém corações/fome**,
     então não há "fantasma" duplicado quando a vida muda.
   - **Sem tela elegível** → comportamento 100% vanilla; a textura é liberada (VRAM devolvida).
3. Depois de `renderWorld`, o jogo desenha o HUD e a GUI normalmente. Ticks, rede, saúde, fome e pacotes do
   servidor **não são tocados**.

### Rastros ao arrastar itens
No vanilla, o passe do mundo é o que limpa o framebuffer a cada quadro. Ao pular esse passe sem substituí-lo,
sobra lixo do quadro anterior (rastros). Aqui **todo pixel é reescrito** a cada quadro (quad opaco do snapshot ou
limpeza para cor sólida) *antes* de HUD e GUI serem desenhados, então nada é apagado da interface e nada do
quadro anterior sobrevive. Além disso, `fixTrails` normaliza flags de estado do OpenGL imediatamente antes de
desenhar telas de contêiner (textura, cor, blend, alpha, depth mask), sem limpar nada.
**A causa dos rastros no seu setup não foi confirmada**; se persistirem, me diga o cliente/mods em uso.

### Modo sem imagem estática
Com `useStaticImage=false` (ou se a captura falhar: textura grande demais, falta de VRAM, erro GL) o mod usa o
modo alternativo: limpa o framebuffer para um cinza escuro. Zero VRAM, zero custo de captura.
VRAM da captura = largura × altura × 4 bytes (≈ 8 MB em 1080p, ≈ 33 MB em 4K), liberada ao fechar.

## O que NÃO foi implementado (e por quê)
- **Pausar ticks do mundo / servidor integrado no singleplayer.** O inventário vanilla não pausa o jogo, e pausar
  o servidor integrado impediria dano e fome de chegar (contradiz a seção 6: corações e fome devem continuar
  reagindo). Pausar só parte dos ticks exigiria mixins/coremod, invasivo demais para ganho pequeno.
  O ganho real de FPS vem de **não renderizar** o mundo.
- **Pausar atualização de partículas/entidades.** Idem: sem hook seguro sem mixin. Elas deixam de ser
  *renderizadas*, que é o custo dominante.
- Uma única otimização extra em `renderOptimizations`: pular o ray-trace `getMouseOver` por tick enquanto congelado.
- Nenhuma meta de FPS é prometida.

## Compatibilidade (nada disto foi testado)
- **OptiFine:** detectado por nome de classe (melhor esforço). Por padrão o hook **não é instalado** com OptiFine
  (ele altera `EntityRenderer`); o mod fica inativo (só `fixTrails` age). `forceRendererHook=true` tenta mesmo assim
  (experimental).
- **Outros mods que substituem `EntityRenderer`:** se a classe não for exatamente `EntityRenderer`, o hook não é instalado.
- Mods que desenham no `RenderWorldLastEvent` (ESP, overlays 3D) **não desenham** enquanto congelado (o passe do mundo é pulado).
- Mods de HUD: o HUD segue ao vivo, então devem funcionar, mas não foi verificado.
- O renderer vanilla antigo continua registrado como *reload listener* (não há API para remover); recarregar
  recursos pode inicializar shaders duas vezes. Efeito esperado: nenhum visível.
- `clientSideOnly = true` e `acceptableRemoteVersions = "*"`: não precisa instalar nada no servidor.

## Configurações (`config/inventoryfreeze.cfg`, também em Mods → Inventory Freeze → Config)
| Opção | Padrão | Efeito |
|---|---|---|
| `enabled` | true | Liga/desliga tudo |
| `freezeScene` | **true** | Congelamento visual do cenário |
| `useStaticImage` | true | Usa imagem capturada (senão, fundo sólido) |
| `renderOptimizations` | true | Pula o ray-trace por tick enquanto congelado |
| `fixTrails` | true | Normaliza estado GL antes de desenhar contêineres |
| `applyToAllContainers` | true | Também em baús etc. (senão só inventário/criativo) |
| `forceRendererHook` | false | Instala o hook mesmo com OptiFine (reiniciar) |

O botão **Default** da tela de config restaura os padrões. Comando no chat: `/invfreeze status | toggle | reset`.

## Compilar no Windows
1. Instale o **JDK 17** (para rodar o Gradle; deixe-o como `JAVA_HOME`) **e também o JDK 8** (o toolchain
   do `build.gradle` compila o mod com ele). Ex.: Eclipse Temurin 8 e 17.
   (Rodar o Gradle só com JDK 8 falha: o plugin `architectury-pack200` exige Java 16+.)
2. Abra o PowerShell na pasta do projeto e rode:
   ```
   gradlew.bat build
   ```
   (a 1ª vez baixa Gradle 7.6.4, Minecraft 1.8.9 e Forge; pode levar vários minutos)
3. O arquivo a usar é `build\libs\InventoryFreeze-1.0.0.jar` (**não** o `-dev`).

### Se o build falhar
O `build.gradle` segue o formato dos templates de 1.8.9 baseados em Loom, mas não foi executado.
Se a resolução de plugins/dependências falhar, a saída mais segura é copiar `src/main/java` e
`src/main/resources` para um template 1.8.9 que você saiba que compila (ex.: o template "Forge1.8.9Template" da comunidade)
e compilar lá. O código-fonte não depende do sistema de build.
Se aparecer erro de **símbolo não encontrado**, o provável culpado é algum nome de API MCP (veja "Pontos de maior risco").

## Instalar
Copie o JAR para `%appdata%\.minecraft\mods` (perfil do Forge 1.8.9 11.15.1.2318).

## Pontos de maior risco no primeiro build/execução
1. `net.minecraftforge.common.config.ConfigElement` e a assinatura de `GuiConfig` (tela de config).
2. `EntityRenderer#getMouseOver(float)` e `renderWorld(float, long)` (nomes `stable_22`).
3. `CommandBase#processCommand` (assinatura do 1.8.9).
4. Estado GL ao final do passe de mundo vs. início do HUD (restauração manual em `SceneFreezer`).
5. Detecção do OptiFine por nome de classe.

## Relatório de testes
| Item da sua lista (seção 10) | Situação |
|---|---|
| Sintaxe Java | ✅ `javac` sem erros de sintaxe (parse; sem classpath do MC) |
| Compilação contra Forge 1.8.9 | ❌ **não realizada** (rede bloqueada, sem JDK 8) |
| JAR | ❌ **não gerado** |
| Abrir/fechar repetido, estabilidade, cursor, arrastar itens, hotbar, pilhas, encantados | ❌ **não testado** — requer Minecraft |
| Saúde e fome com inventário aberto | ⚠️ só análise de código: HUD e ticks não são alterados, então devem continuar ao vivo — **não testado** |
| Singleplayer / multiplayer / desconexão com inventário aberto | ⚠️ só análise de código (nenhum hook de rede; textura liberada no tick seguinte) — **não testado** |
| Restauração do cenário ao fechar | ⚠️ análise de código (`currentScreen == null` → caminho vanilla) — **não testado** |
| Resoluções diferentes / redimensionar | ⚠️ análise de código (snapshot recapturado se o tamanho mudar) — **não testado** |
| Comparação de desempenho ligado/desligado | ❌ **não medida** |
| Ausência de rastros | ❌ **não verificado** |
