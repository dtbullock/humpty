package co.mewf.humpty.spi.processors;

import co.mewf.humpty.config.Context;
import co.mewf.humpty.config.PreProcessorContext;

public class TestProcessor implements BundleProcessor, AssetProcessor, SourceProcessor {

  @Override
  public String getName() {
    return "test";
  }
  
  @Override
  public boolean accepts(String asset) {
    return true;
  }

  @Override
  public CompilationResult compile(SourceProcessor.CompilationResult compilationResult, PreProcessorContext context) {
    return new CompilationResult(compilationResult.getAssetName(), "Compiled!" + compilationResult.getAsset());
  }

  @Override
  public String processAsset(String assetName, String asset, PreProcessorContext context) {
    return "Preprocessed!" + asset;
  }

  @Override
  public String processBundle(String assetName, String asset, Context context) {
      return asset + "Postprocessed!";
  }

}
