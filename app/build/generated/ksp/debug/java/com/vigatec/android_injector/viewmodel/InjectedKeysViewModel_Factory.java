package com.vigatec.android_injector.viewmodel;

import com.example.persistence.repository.InjectedKeyRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class InjectedKeysViewModel_Factory implements Factory<InjectedKeysViewModel> {
  private final Provider<InjectedKeyRepository> injectedKeyRepositoryProvider;

  public InjectedKeysViewModel_Factory(
      Provider<InjectedKeyRepository> injectedKeyRepositoryProvider) {
    this.injectedKeyRepositoryProvider = injectedKeyRepositoryProvider;
  }

  @Override
  public InjectedKeysViewModel get() {
    return newInstance(injectedKeyRepositoryProvider.get());
  }

  public static InjectedKeysViewModel_Factory create(
      Provider<InjectedKeyRepository> injectedKeyRepositoryProvider) {
    return new InjectedKeysViewModel_Factory(injectedKeyRepositoryProvider);
  }

  public static InjectedKeysViewModel newInstance(InjectedKeyRepository injectedKeyRepository) {
    return new InjectedKeysViewModel(injectedKeyRepository);
  }
}
