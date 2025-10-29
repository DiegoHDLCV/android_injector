package com.vigatec.android_injector.viewmodel;

import android.app.Application;
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
public final class SplashViewModel_Factory implements Factory<SplashViewModel> {
  private final Provider<Application> applicationProvider;

  public SplashViewModel_Factory(Provider<Application> applicationProvider) {
    this.applicationProvider = applicationProvider;
  }

  @Override
  public SplashViewModel get() {
    return newInstance(applicationProvider.get());
  }

  public static SplashViewModel_Factory create(Provider<Application> applicationProvider) {
    return new SplashViewModel_Factory(applicationProvider);
  }

  public static SplashViewModel newInstance(Application application) {
    return new SplashViewModel(application);
  }
}
