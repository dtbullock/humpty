package co.mewf.humpty;

import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;

import co.mewf.humpty.config.Bundle;
import co.mewf.humpty.config.Configuration;
import co.mewf.humpty.config.Configuration.Mode;
import co.mewf.humpty.config.Context;
import co.mewf.humpty.config.PreProcessorContext;
import co.mewf.humpty.spi.bundles.BundleResolver;
import co.mewf.humpty.spi.listeners.PipelineListener;
import co.mewf.humpty.spi.processors.AssetProcessor;
import co.mewf.humpty.spi.processors.BundleProcessor;
import co.mewf.humpty.spi.processors.SourceProcessor;
import co.mewf.humpty.spi.processors.SourceProcessor.CompilationResult;
import co.mewf.humpty.spi.resolvers.Resolver;

public class Pipeline {
  
  public static class Output {
    private final String fileName;
    private final String asset;
    
    public Output(String fileName, String asset) {
      this.fileName = fileName;
      this.asset = asset;
    }

    public String getFileName() {
      return fileName;
    }

    public String getAsset() {
      return asset;
    }
  }

  private final List<Resolver> resolvers;
  private final List<AssetProcessor> assetProcessors;
  private final List<BundleProcessor> bundleProcessors;
  private final List<SourceProcessor> compilingProcessors;
  private final List<PipelineListener> pipelineListeners;
  private final List<? extends BundleResolver> bundleResolvers;

  public Pipeline(List<? extends BundleResolver> bundleResolvers, List<? extends Resolver> resolvers, List<? extends SourceProcessor> compilingProcessors, List<? extends AssetProcessor> assetProcessors, List<? extends BundleProcessor> bundleProcessors, List<PipelineListener> pipelineListeners) {
    this.bundleResolvers = bundleResolvers;
    this.resolvers = Collections.unmodifiableList(resolvers);
    this.compilingProcessors = Collections.unmodifiableList(compilingProcessors);
    this.assetProcessors = Collections.unmodifiableList(assetProcessors);
    this.bundleProcessors = Collections.unmodifiableList(bundleProcessors);
    this.pipelineListeners = Collections.unmodifiableList(pipelineListeners);
  }

  public Pipeline.Output process(String originalAssetName) {
    return process(originalAssetName, originalAssetName.indexOf('/') > -1 ? Configuration.Mode.DEVELOPMENT : Configuration.Mode.PRODUCTION);
  }

  private Pipeline.Output process(String originalAssetName, Mode mode) {
    Bundle bundle = getBundle(originalAssetName);

    Context context = new Context(mode, bundle);
    AtomicReference<String> finalAssetName = new AtomicReference<String>();
    
    String processedBundle = bundle.stream().map(bundledAsset -> {
        return resolvers.stream()
          .filter(r -> r.accepts(bundledAsset))
          .findFirst()
          .orElseThrow(illegal("There is no resolver for asset: " + bundledAsset))
          .resolve(bundledAsset, context);
      })
      .flatMap(List::stream)
      .map(assetFile -> {
        PreProcessorContext preprocessorContext = context.getPreprocessorContext(assetFile.getPath());
        SourceProcessor.CompilationResult compilationResult = compile(assetFile.getPath(), assetFile.getContents(), preprocessorContext);
        String processedAsset = processAsset(compilationResult.getAssetName(), compilationResult.getAsset(), preprocessorContext);
        finalAssetName.set(compilationResult.getAssetName());
        
        return processedAsset;
      })
      .collect(joining("\n", "", "\n"));

    try {
      return new Output(finalAssetName.get(), processBundle(processedBundle, context));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  @SuppressWarnings("unchecked")
  public <T extends PipelineListener> Optional<T> getPipelineListener(Class<T> pipelineListenerClass) {
    return (Optional<T>) pipelineListeners.stream().filter(l -> l.getClass() == pipelineListenerClass).findFirst();
  }
  
  private Bundle getBundle(String originalAssetName) {
    return bundleResolvers.stream()
                          .filter(br -> br.accepts(originalAssetName))
                          .findFirst()
                          .map(br -> br.resolve(originalAssetName))
                          .orElseThrow(illegal("There is no bundle named " + originalAssetName));
  }

  private CompilationResult compile(String assetName, String asset, PreProcessorContext context) {
    return compilingProcessors.stream().reduce(new CompilationResult(assetName, asset), SourceProcessor.maybe(assetName, context), ignored());
  }

  private String processAsset(String assetName, String initialAsset, PreProcessorContext context) {
    String processedAsset = assetProcessors.stream().filter(p -> p.accepts(assetName))
        .reduce(initialAsset, (asset, processor) -> processor.processAsset(assetName, asset, context), ignored());
    
    pipelineListeners.forEach(listener -> listener.onAssetProcessed(processedAsset, assetName, context.getAssetUrl(), context.getBundle()));

    return processedAsset;
  }
  
  private String processBundle(String asset, Context context) throws IOException {
    String processedBundle = bundleProcessors.stream().filter(p -> p.accepts(context.getBundleName()))
        .reduce(asset, (a, p) -> p.processBundle(context.getBundleName(), a, context), ignored());

    pipelineListeners.forEach(listener -> listener.onBundleProcessed(processedBundle, context.getBundleName()));
    
    return processedBundle;
  }
  
  private <T> BinaryOperator<T> ignored() {
    return (a, b) -> b;
  }
  
  private Supplier<? extends RuntimeException> illegal(String message) {
    return () -> new IllegalArgumentException(message);
  }
}
