using Xunit;

// Each fixture owns a PostgreSQL pool; stay within the shared Supabase session limit.
[assembly: CollectionBehavior(MaxParallelThreads = 1)]
