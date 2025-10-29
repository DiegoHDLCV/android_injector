package com.vigatec.android_injector.viewmodel;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class MasterKeyEntryViewModel_Factory implements Factory<MasterKeyEntryViewModel> {
  @Override
  public MasterKeyEntryViewModel get() {
    return newInstance();
  }

  public static MasterKeyEntryViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MasterKeyEntryViewModel newInstance() {
    return new MasterKeyEntryViewModel();
  }

  private static final class InstanceHolder {
    private static final MasterKeyEntryViewModel_Factory INSTANCE = new MasterKeyEntryViewModel_Factory();
  }
}
