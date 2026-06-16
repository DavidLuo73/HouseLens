using HouseLens.Application.Scoring;
using HouseLens.Domain.Entities;
using FluentAssertions;

namespace HouseLens.UnitTests.Scoring;

public class ScoreCalculatorTests
{
    private static readonly ScoringConfig DefaultConfig = new()
    {
        WeightUnitPrice = 0.40m,
        WeightAge = 0.25m,
        WeightParking = 0.20m,
        WeightLocation = 0.15m
    };

    [Fact]
    public void Calculate_AllFactorsPresent_ScoreInRange0To1()
    {
        var property = new Property
        {
            CurrentUnitPrice = 25m,
            AgeYears = 5,
            HasParking = true,
            District = "中和區"
        };

        var score = ScoreCalculator.Calculate(property, DefaultConfig, avgUnitPrice: 28m);

        score.Should().BeInRange(0m, 1m);
    }

    [Fact]
    public void Calculate_MissingAge_RenormalizesWeights()
    {
        var property = new Property
        {
            CurrentUnitPrice = 25m,
            AgeYears = null,
            HasParking = false,
            District = "中壢區"
        };

        var score = ScoreCalculator.Calculate(property, DefaultConfig, avgUnitPrice: 28m);

        score.Should().BeInRange(0m, 1m);
        score.Should().BeGreaterThan(0m);
    }

    [Fact]
    public void Calculate_BetterPrice_ScoresHigher()
    {
        var propGood = new Property { CurrentUnitPrice = 20m, AgeYears = 5, HasParking = true, District = "中和區" };
        var propBad = new Property { CurrentUnitPrice = 35m, AgeYears = 5, HasParking = true, District = "中和區" };
        var config = DefaultConfig;
        var avgPrice = 28m;

        var scoreGood = ScoreCalculator.Calculate(propGood, config, avgPrice);
        var scoreBad = ScoreCalculator.Calculate(propBad, config, avgPrice);

        scoreGood.Should().BeGreaterThan(scoreBad);
    }

    [Fact]
    public void Calculate_MissingUnitPrice_RenormalizesWeights()
    {
        var property = new Property
        {
            CurrentUnitPrice = null,
            AgeYears = 10,
            HasParking = true,
            District = "板橋區"
        };

        var score = ScoreCalculator.Calculate(property, DefaultConfig, avgUnitPrice: 25m);

        score.Should().BeInRange(0m, 1m);
        score.Should().BeGreaterThan(0m);
    }

    [Fact]
    public void Calculate_WithParking_ScoresHigherThanWithout()
    {
        var propWith = new Property { CurrentUnitPrice = 26m, AgeYears = 10, HasParking = true, District = "永和區" };
        var propWithout = new Property { CurrentUnitPrice = 26m, AgeYears = 10, HasParking = false, District = "永和區" };

        var scoreWith = ScoreCalculator.Calculate(propWith, DefaultConfig, avgUnitPrice: 28m);
        var scoreWithout = ScoreCalculator.Calculate(propWithout, DefaultConfig, avgUnitPrice: 28m);

        scoreWith.Should().BeGreaterThan(scoreWithout);
    }

    [Fact]
    public void Calculate_ZeroAvgUnitPrice_SkipsUnitPriceFactor()
    {
        var property = new Property
        {
            CurrentUnitPrice = 25m,
            AgeYears = 5,
            HasParking = false,
            District = "新店區"
        };

        var score = ScoreCalculator.Calculate(property, DefaultConfig, avgUnitPrice: 0m);

        score.Should().BeInRange(0m, 1m);
        score.Should().BeGreaterThan(0m);
    }

    [Fact]
    public void Calculate_NewerProperty_ScoresHigherThanOlderProperty()
    {
        var propNew = new Property { CurrentUnitPrice = 28m, AgeYears = 0, HasParking = false, District = "中壢區" };
        var propOld = new Property { CurrentUnitPrice = 28m, AgeYears = 30, HasParking = false, District = "中壢區" };

        var scoreNew = ScoreCalculator.Calculate(propNew, DefaultConfig, avgUnitPrice: 28m);
        var scoreOld = ScoreCalculator.Calculate(propOld, DefaultConfig, avgUnitPrice: 28m);

        scoreNew.Should().BeGreaterThan(scoreOld);
    }

    [Fact]
    public void Calculate_UnknownDistrict_UsesDefaultLocationScore()
    {
        var property = new Property
        {
            CurrentUnitPrice = 25m,
            AgeYears = 10,
            HasParking = false,
            District = "未知區"
        };

        var score = ScoreCalculator.Calculate(property, DefaultConfig, avgUnitPrice: 28m);

        score.Should().BeInRange(0m, 1m);
        score.Should().BeGreaterThan(0m);
    }
}
