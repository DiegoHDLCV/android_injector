package com.vigatec.android_injector.viewmodel;

import android.app.Application;
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
public final class MainViewModel_Factory implements Factory<MainViewModel> {
  private final Provider<InjectedKeyRepository> injectedKeyRepositoryProvider;

  private final Provider<Application> applicationProvider;

  public MainViewModel_Factory(Provider<InjectedKeyRepository> injectedKeyRepositoryProvider,
      Provider<Application> applicationProvider) {
    this.injectedKeyRepositoryProvider = injectedKeyRepositoryProvider;
    this.applicationProvider = applicationProvider;
  }

  @Override
  public MainViewModel get() {
    return newInstance(injectedKeyRepositoryProvider.get(), applicationProvider.get());
  }

  public static MainViewModel_Factory create(
      Provider<InjectedKeyRepository> injectedKeyRepositoryProvider,
      Provider<Application> applicationProvider) {
    return new MainViewModel_Factory(injectedKeyRepositoryProvider, applicationProvider);
  }

  public static MainViewModel newInstance(InjectedKeyRepository injectedKeyRepository,
      Application application) {
    return new MainViewModel(injectedKeyRepository, application);
  }
}
